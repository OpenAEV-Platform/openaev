package io.openaev.api.chaining;

import static io.openaev.database.model.Command.COMMAND_TYPE;
import static io.openaev.database.model.DnsResolution.DNS_RESOLUTION_TYPE;
import static io.openaev.database.model.Executable.EXECUTABLE_TYPE;
import static io.openaev.database.model.FileDrop.FILE_DROP_TYPE;
import static io.openaev.service.chaining.StepService.setField;
import static io.openaev.utils.JsonUtils.gson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.*;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Executor;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.StructuredOutputUtils;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.*;
import io.openaev.service.chaining.ConditionService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowStateService;
import io.openaev.utils.ConditionUtils;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.TargetType;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ActionStep} for executing Inject steps.
 *
 * <p>Handles creation, readying, running, updating, and ending of steps that use the {@link
 * StepActionClass#INJECT_EXECUTION} action.
 *
 * <p>Responsible for:
 *
 * <ul>
 *   <li>Creating step templates and ready steps
 *   <li>Serializing/deserializing step data (InjectInput → Inject)
 *   <li>Executing injects using {@link Executor}
 *   <li>Updating step output with execution traces
 *   <li>Handling inject statuses and errors
 * </ul>
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class InjectExecutionStep implements ActionStep {

  private final InjectorContractService injectorContractService;
  private final UserService userService;
  private final AssetService assetService;
  private final TeamService teamService;
  private final TagService tagService;
  private final DocumentService documentService;
  private final InjectService injectService;
  private final TagRuleService tagRuleService;
  private final AssetGroupService assetGroupService;
  private final ConditionService conditionService;
  private final WorkflowStateService workflowStateService;

  private final InjectorContractRepository injectorContractRepository;

  private final StructuredOutputUtils structuredOutputUtils;
  private final InjectorContractContentUtils injectorContractContentUtils;
  private final ConditionUtils conditionUtils;
  private final InjectUtils injectUtils;

  private final Executor executor;

  @Resource protected ObjectMapper mapper;
  @PersistenceContext private EntityManager em;

  /**
   * Creates a new step template for an inject execution.
   *
   * @param newStep the new step from front
   * @param workflow the workflow template this step belongs to
   * @return a step in TEMPLATE status
   */
  @Override
  public Optional<Step> create(StepsCreateInput.StepInput newStep, Workflow workflow)
      throws ChainingException {
    String data = null;

    if (workflow.getScenario() != null) {
      data = stepData(newStep, null, workflow.getScenario());

    } else if (workflow.getSimulation() != null) {
      data = stepData(newStep, workflow.getSimulation(), null);
    }
    if (data == null) {
      throw new ChainingException(
          "New step (TEMPLATE): Error processing Inject. Workflow has no simulation or scenario");
    }

    String input = stepInputFromConditionMapper(newStep.getConditions());
    // TODO: get outputParser
    String outputParser = this.stepOutputParser("");
    Step stepTemplate =
        Step.builder()
            .data(data)
            .input(input)
            .outputParser(outputParser)
            .status(StepStatus.TEMPLATE)
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .workflow(workflow)
            .build();
    return Optional.of(stepTemplate);
  }

  /**
   * Creates a Ready step from a step template.
   *
   * <p>The step is initialized in READY status and contains the same data as the template.
   *
   * @param stepTemplate the template step to duplicate
   * @param input the input to apply for this execution
   * @param workflowRun the workflow run this step belongs to
   * @return a step in READY status ready to be executed
   */
  @Override
  public Optional<Step> ready(Step stepTemplate, String input, Workflow workflowRun)
      throws ChainingException {
    // CALL BY when new input or start simulation
    Step readyStep = new Step();
    readyStep.setWorkflow(workflowRun);
    readyStep.setData(stepTemplate.getData());
    readyStep.setStepTemplate(stepTemplate);
    // TODO manage input from output paser from payload or nuclei or nmap
    readyStep.setInput(input);
    readyStep.setStatus(StepStatus.READY);
    readyStep.setStepAction(StepActionClass.INJECT_EXECUTION);
    readyStep.setLimitExecution(stepTemplate.getLimitExecution());

    return Optional.of(readyStep);
  }

  /**
   * Runs a READY step by executing the corresponding to inject.
   *
   * <p>Handles deserialization of step data, creation of the inject, execution via {@link
   * Executor}, and updates the step data with inject ID.
   *
   * @param readyStep the step currently in READY status
   * @return the updated step with execution info, or null if execution fails
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public Optional<Step> run(Step readyStep) throws ChainingException {
    // CALL BY QUEUE READY
    Inject inject = getInjectFromDataStep(readyStep);
    // CREATE & SAVE INJECT

    inject = injectService.createInject(inject);
    String injectId = inject.getId();
    prepareGetStatusPayloadFromInject(inject.getInjectorContract().get());

    try {
      String data = setInjectId(inject.getId(), readyStep.getData());
      readyStep.setData(data);

      // EXECUTE INJECT
      ExecutableInject executableInject =
          new ExecutableInject(
              true,
              true,
              inject,
              inject.getTeams(),
              inject.getAssets(),
              inject.getAssetGroups(),
              List.of()); // TODO Check users?

      // TODO Check add documents? Executable Payloads
      // executableInject.addDirectAttachment(inject.getDocuments());

      executor.directExecute(executableInject);
      return Optional.of(readyStep);
    } catch (Exception e) {
      throw new ChainingException(
          "Inject execution failed. Inject ID: " + injectId + " (transaction rolled back)", e);
    }
  }

  /** Loads the concrete payload subtype (Command, Executable, etc.) into the injector contract. */
  private void prepareGetStatusPayloadFromInject(InjectorContract injectorContract) {
    if (injectorContract.getPayload() == null) {
      return;
    }
    Payload payload = injectorContract.getPayload();
    if (COMMAND_TYPE.equals(injectorContract.getPayload().getType())) {
      injectorContract.setPayload(em.find(Command.class, payload.getId()));
    }
    if (EXECUTABLE_TYPE.equals(injectorContract.getPayload().getType())) {
      injectorContract.setPayload(em.find(Executable.class, payload.getId()));
    }
    if (FILE_DROP_TYPE.equals(injectorContract.getPayload().getType())) {
      injectorContract.setPayload(em.find(FileDrop.class, payload.getId()));
    }
    if (DNS_RESOLUTION_TYPE.equals(injectorContract.getPayload().getType())) {
      injectorContract.setPayload(em.find(DnsResolution.class, payload.getId()));
    }
  }

  /**
   * Updates a step after execution.
   *
   * <p>Retrieves the inject status and execution traces, formats them into the step output.
   *
   * @param stepRun the executed step to update
   * @return the step with updated output, or null if inject not found
   */
  @Override
  public Optional<Step> update(Step stepRun) throws ChainingException {
    // GET INJECT
    String data = stepRun.getData();
    String injectId = StepService.getField(data, "inject_id");
    Inject inject = injectService.inject(injectId);

    // GET INJECT STATUS
    InjectStatus injectStatus = inject.getStatus().orElse(null);

    List<Map<String, JsonElement>> output = new ArrayList<>();
    if (injectStatus != null) {
      // FORMAT EXECUTION TRACE TO OUTPUT STEP
      formatExecutionTracesToOutput(injectStatus, output);
    }

    // TODO FORMAT INJECT STATUS TO OUTPUT STEP
    formatStatusToOutput(output);
    // TODO FORMAT COLLECTOR EXPECTATION TO OUTPUT STEP
    formatCollectorExpectationToOutput(output);
    // TODO FORMAT EXPIRATION MANAGER TO OUTPUT STEP
    formatExpirationManagerToOutput(output);
    // TODO FORMAT MANUAL UPDATE TO OUTPUT STEP
    formatManualUpdateToOutput(output);

    // UPDATE step output
    if (!output.isEmpty()) {
      JsonElement elements = gson.toJsonTree(output);
      JsonObject jsonObject = new JsonObject();
      jsonObject.add("outputs", elements);

      // UPDATE step output
      stepRun.setOutput(jsonObject.toString());
      // PROPAGATE state changes into engine if parsed output is present
      processOutputAndStateSync(stepRun, output, inject);

      return Optional.of(stepRun);
    }

    log.info("Inject output not found. ID:  {}", injectId);
    return Optional.empty();
  }

  /**
   * Syncs parsed execution output into workflow global state, potentially triggering chained steps.
   */
  private void processOutputAndStateSync(
      Step stepRun, List<Map<String, JsonElement>> output, Inject inject) {
    boolean hasParsedData = output.stream().anyMatch(map -> map.containsKey("parsed"));

    if (hasParsedData) {
      Map<String, List<String>> outputData = extractDataFromParsed(output);

      if (!outputData.isEmpty()) {
        Workflow workflowRun = stepRun.getWorkflow();

        Map<String, ContractOutputType> fieldTypeMap = buildFieldTypeMapFromInject(inject);
        // Sync global state with the execution output, which may trigger chained steps to become
        // READY
        workflowStateService.syncState(gson.toJsonTree(outputData), fieldTypeMap, workflowRun);
      }
    }
  }

  /** Extracts key-value pairs from structured "parsed" output entries. */
  private Map<String, List<String>> extractDataFromParsed(List<Map<String, JsonElement>> output) {
    Map<String, List<String>> result = new HashMap<>();

    try {
      for (Map<String, JsonElement> entry : output) {
        if (entry.containsKey("parsed")) {
          JsonObject parsed = entry.get("parsed").getAsJsonObject();
          if (parsed.has("_children")) {
            JsonObject children = parsed.getAsJsonObject("_children");

            for (String key : children.keySet()) {
              JsonArray valuesArray = children.getAsJsonObject(key).getAsJsonArray("_children");
              for (JsonElement item : valuesArray) {
                String val = item.getAsJsonObject().get("_value").getAsString();
                // SyncState keys are usually Uppercase (e.g., "IP")
                result.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("Failed to parse structured output for synchronize global State", e);
      return Collections.emptyMap();
    }
    return result;
  }

  /**
   * Ends a step and checks whether the workflow can be marked as finished.
   *
   * @param stepRun the step to end
   * @param workflow the workflow containing the step
   */
  @Override
  public void end(Step stepRun, Workflow workflow) {
    // todo Condition end of step
    // todo check if every output has been received
    // Get all step with id workflow = X if all end workflow = END;
  }

  // -------------------
  // Helper methods
  // -------------------

  /**
   * Builds and serializes the inject data for a step.
   *
   * <p>Creates an {@link Inject} instance from the step input and injector contract, enriches it
   * with user context, targets (teams, assets, asset groups), tags, documents, and optional
   * simulation data.
   *
   * <p>If the inject content is missing, default values are loaded from the injector contract.
   *
   * @param step the step creation input containing the inject definition
   * @param simulation the simulation context, if any
   * @return a JSON string representing the serialized inject, or {@code null} if the injector
   *     contract is missing
   */
  private String stepData(StepsCreateInput.StepInput step, Exercise simulation, Scenario scenario)
      throws ChainingException {

    InjectInput data = (InjectInput) step.getDataStep();

    if (data == null) {
      throw new IllegalArgumentException("Data step of new step (TEMPLATE) is null");
    }

    if (data.getInjectorContract() == null) {
      throw new IllegalArgumentException(
          "Data step of new step (TEMPLATE) do not contain injector contract");
    }

    if ((simulation == null && scenario == null) || (simulation != null && scenario != null)) {
      throw new IllegalArgumentException("Exactly one of exercise or scenario should be present");
    }

    InjectorContract injectorContract =
        this.injectorContractService.injectorContract(data.getInjectorContract());

    Injector injector =
        injectUtils.resolveInjectorReference(data.getInjectorId(), injectorContract);
    Inject inject = data.toInject(injectorContract, injector);
    inject.setUser(this.userService.currentUser());

    inject.setTeams(teamService.getTeamsByIds(data.getTeams()));
    inject.setAssets(assetService.assets(data.getAssets()));

    inject.setTags(tagService.tagSet(data.getTagIds()));

    List<InjectDocument> injectDocuments =
        data.getDocuments().stream()
            .map(i -> i.toDocument(documentService.document(i.getDocumentId()), inject))
            .toList();
    inject.setDocuments(injectDocuments);
    Set<Tag> tags = new HashSet<>();
    // TODO copy from io/openaev/rest/inject/service/InjectService.java:178
    // EXERCISE
    if (simulation != null) {
      tags = simulation.getTags();
      inject.setExercise(simulation);
      // Linked documents directly to the simulation
      inject
          .getDocuments()
          .forEach(
              document -> {
                if (!document.getDocument().getExercises().contains(simulation)) {
                  simulation.getDocuments().add(document.getDocument());
                }
              });
    }
    // SCENARIO
    if (scenario != null) {
      tags = scenario.getTags();
      // todo to brainstorm did we need Document on scenario ? why ?
      // Linked documents directly to the scenario
      inject
          .getDocuments()
          .forEach(
              document -> {
                if (!document.getDocument().getScenarios().contains(scenario)) {
                  scenario.getDocuments().add(document.getDocument());
                }
              });
    }
    // verify if inject is not manual/sms/emails...
    if (injectService.canApplyTargetType(inject, TargetType.ASSETS_GROUPS)) {
      // add default asset groups
      inject.setAssetGroups(
          this.tagRuleService.applyTagRuleToInjectCreation(
              tags.stream().map(Tag::getId).toList(),
              assetGroupService.assetGroups(data.getAssetGroups())));
    }

    // if inject content is null we add the defaults from the injector contract
    // this is the case when creating an inject from OpenCti
    if (inject.getContent() == null || inject.getContent().isEmpty()) {
      inject.setContent(
          injectorContractContentUtils.getDynamicInjectorContractFieldsForInject(injectorContract));
    }
    ObjectMapper om =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    try {
      return om.writeValueAsString(inject);
    } catch (JsonProcessingException e) {
      throw new ChainingException("New step (TEMPLATE): Error processing Inject to JSON", e);
    }
  }

  /**
   * Returns the active output parsers at given time
   *
   * @param data data to process
   * @return json with outputParser
   */
  private String stepOutputParser(String data) {
    // TODO
    // inject.getPayload().get().getOutputParsers();
    // Nmap
    // Nuclei
    return "{}";
  }

  /**
   * Builds the step input from MAPPER conditions.
   *
   * <p>Extracts all conditions of type {@link ConditionType#MAPPER} and converts them into an input
   * mapping structure used by the step execution.
   *
   * <p>Each mapping contains:
   *
   * <ul>
   *   <li>{@code key} – the target input key
   *   <li>{@code path} – the JSON path to extract the value
   * </ul>
   *
   * @param conditions the list of conditions to process
   * @return a JSON string representing the mapped step input, or an empty JSON object if none
   */
  private static String stepInputFromConditionMapper(List<ConditionCreateInput> conditions) {
    if (conditions == null || conditions.isEmpty()) {
      return "{}";
    }
    List<Map<String, Object>> inputs = new ArrayList<>();

    for (ConditionCreateInput condition : conditions) {
      if (ConditionType.MAPPER.equals(condition.getType())) {

        Map<String, Object> input = new HashMap<>();
        input.put("key", condition.getKey());
        input.put("keyType", condition.getKeyType() != null ? condition.getKeyType().name() : null);
        input.put("path", condition.getValue());
        input.put("mappingType", condition.getMappingType());
        input.put("id_step_from", condition.getStepFrom());
        input.put("value", condition.getValue());

        inputs.add(input);
      }
    }

    Map<String, Object> result = Map.of("input", inputs);
    return gson.toJson(result);
  }

  /**
   * @param injectId id of inject
   * @param dataStep json of inject
   * @return json updated
   */
  private String setInjectId(String injectId, String dataStep) {
    return setField(dataStep, "inject_id", injectId);
  }

  /**
   * Converts an {@link InjectInput} into a list of {@link StepsCreateInput.StepInput}.
   *
   * @param input the inject input
   * @return step input
   */
  public static StepsCreateInput.StepInput getInjectAsStepsCreateInput(InjectInput input) {
    StepsCreateInput.StepInput stepCreateInput = new StepsCreateInput.StepInput();
    stepCreateInput.setDataStep(input);
    stepCreateInput.setStepAction(StepActionClass.INJECT_EXECUTION);

    if (input.getDependsDuration() != 0) {
      ConditionCreateInput conditionCreateInput =
          ConditionCreateInput.builder()
              .temporaryId("0")
              .type(ConditionType.AFTER)
              .key(null)
              .keyType(null)
              .mappingType(null)
              .value(String.valueOf(input.getDependsDuration()))
              .build();
      stepCreateInput.setConditions(List.of(conditionCreateInput));
    }
    // TODO DEPEND ON

    return stepCreateInput;
  }

  /**
   * Extracts an {@link Inject} object from the JSON data stored in a {@link Step}.
   *
   * <p>This method performs the following steps:
   *
   * <ol>
   *   <li>Deserializes the step's JSON data into an {@link Inject} object using {@link
   *       ObjectMapper}.
   *   <li>Parses the JSON to locate the associated {@link InjectorContract} and its {@link
   *       Injector}.
   *   <li>If the {@link Injector} is missing in the contract, it attempts to fetch it from the
   *       database using the {@link EntityManager}.
   *   <li>Logs warnings or info messages when required entities are not found.
   * </ol>
   *
   * <p>Notes:
   *
   * <ul>
   *   <li>If the step JSON does not contain a valid injector contract or injector, the method
   *       returns {@code null}.
   *   <li>If any {@link JsonProcessingException} or {@link IllegalArgumentException} occurs during
   *       parsing, the exception is logged and {@code null} is returned.
   * </ul>
   *
   * @param step the {@link Step} containing the JSON data for the inject
   * @return the deserialized {@link Inject} object with its injector set if found; {@code null} if
   *     the injector contract is missing or if an exception occurs during deserialization
   */
  private Inject getInjectFromDataStep(Step step) throws ChainingException {
    ObjectMapper om =
        new ObjectMapper()
            .findAndRegisterModules()
            .setInjectableValues(new InjectableValues.Std().addValue(EntityManager.class, em))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    try {
      // GET INJECT FROM JSON
      Inject inject = om.readValue(step.getData(), Inject.class);

      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(step.getData());

      // GET INJECTOR CONTRACT
      try {
        Hibernate.initialize(inject.getInjectorContract().get());
      } catch (Exception e) {
        throw new ChainingException(
            "Injector contract not found for step (READY) ID: " + step.getId());
      }

      InjectorContract injectorContract =
          injectorContractRepository
              .findById(inject.getInjectorContract().get().getId())
              .orElseThrow(
                  () ->
                      new ChainingException(
                          "Injector contract not found for step (READY) ID: " + step.getId()));

      injectorContract.setCompositeId(
          new InjectorContractId(injectorContract.getId(), TenantContext.getCurrentTenant()));
      inject.setInjectorContract(injectorContract);

      // GET INJECTOR
      JsonNode injectorNode = root.path("inject_injector");

      // INJECTOR ID FROM JSON NULL
      if ((injectorNode.isMissingNode() || injectorNode.asText().isEmpty())) {

        throw new ChainingException(
            "Injector not found for injectorContractId "
                + injectorContract.getId()
                + " and step (READY) ID "
                + step.getId());

        // GET INJECTOR FROM DB
      } else {

        String injectorId = injectorNode.asText();
        Injector injector = inject.getInjector();

        try {
          Hibernate.initialize(inject.getInjector());
        } catch (Exception e) {
          throw new ChainingException(
              "Injector not found for injectorId "
                  + injectorId
                  + " and step (READY) ID "
                  + step.getId());
        }
        injector.setTenant(injectorContract.getTenant());
        inject.setInjector(injector);
      }

      // Modify payload arguments with inputs from step
      ObjectNode updatedContent = updateContentWithInputs(step, injectorContract.getContent());
      inject.setContent(updatedContent);

      return inject;

    } catch (JsonProcessingException e) {
      throw new ChainingException("Step (READY) : Error processing JSON to Inject ", e);
    }
  }

  /**
   * Merges step input values into injector contract content to build runtime payload arguments.
   *
   * <p>This method reads the current contract content JSON, fetches input values resolved during
   * chaining from {@link Step#getInput()}, then maps those values to contract keys using MAPPER
   * conditions. The resulting JSON is used as inject content for execution.
   *
   * @param step the READY step containing resolved input values used as payload arguments
   * @param contentJson base injector contract content JSON
   * @return updated contract content with mapped input values injected; empty object if parsing
   *     fails
   */
  private ObjectNode updateContentWithInputs(Step step, @NotBlank String contentJson) {
    if (contentJson == null || contentJson.isBlank()) {
      return mapper.createObjectNode();
    }

    try {
      ObjectNode contentNode = (ObjectNode) mapper.readTree(contentJson);

      String inputJson = step.getInput();
      if (inputJson == null || inputJson.isEmpty() || "{}".equals(inputJson)) {
        return contentNode;
      }

      JsonNode inputValues = mapper.readTree(inputJson);

      conditionService.findAllConditionsByStepId(step.getId()).stream()
          .filter(conditionUtils::isMapperCondition)
          .forEach(mapping -> applyMapping(contentNode, mapping, inputValues));

      conditionService.findAllConditionsByStepId(step.getId()).stream()
          .filter(conditionUtils::isMapperCondition)
          .toList();

      return contentNode;

    } catch (JsonProcessingException e) {
      return mapper.createObjectNode();
    }
  }

  /**
   * Maps a value from the input source to the target content node based on the condition's key type
   * and target key.
   *
   * @param contentNode the JSON object to be updated
   * @param mapping the condition defining the source and target keys
   * @param inputValues the source JSON containing the values to map
   */
  private void applyMapping(ObjectNode contentNode, Condition mapping, JsonNode inputValues) {
    String inputKey = mapping.getKeyType().name(); // e.g., "IPv4"
    String targetJsonKey = mapping.getKey();

    if (inputValues.has(inputKey)) {
      contentNode.set(targetJsonKey, inputValues.get(inputKey));
    }
  }

  /**
   * Formats execution traces into a structured step output.
   *
   * <p>Converts {@link ExecutionTrace} entries from the inject status into a list of
   * JSON-compatible maps. Each entry contains:
   *
   * <ul>
   *   <li>{@code agent_id} – the ID of the agent that produced the trace
   *   <li>{@code parsed} – the structured output when available
   *   <li>{@code message} – the raw message when structured output is not available
   * </ul>
   *
   * @param injectStatus the inject status containing execution traces
   * @param output the output list to populate
   */
  private void formatExecutionTracesToOutput(
      InjectStatus injectStatus, List<Map<String, JsonElement>> output) {
    // GET EXECUTION TRACE
    List<ExecutionTrace> traces = injectStatus.getTraces();
    log.info("[Chaining] formatExecutionTracesToOutput — traces count: {}", traces.size());
    for (ExecutionTrace trace : traces) {
      Map<String, JsonElement> map = new HashMap<>();
      if (trace.getAgent() == null) {
        log.info("[Chaining] Trace skipped: agent is null");
        continue;
      }
      map.put("agent_id", gson.toJsonTree(trace.getAgent().getId()));
      if (trace.getStructuredOutput() != null) {
        log.info(
            "[Chaining] Trace has structuredOutput: {}", trace.getStructuredOutput().toString());
        map.put("parsed", gson.toJsonTree(trace.getStructuredOutput()));
      } else {
        log.info("[Chaining] Trace has NO structuredOutput, message: {}", trace.getMessage());
        try {
          map.put("message", JsonParser.parseString(trace.getMessage()));
        } catch (JsonSyntaxException | IllegalStateException e) {
          map.put("message", gson.toJsonTree(trace.getMessage()));
        }
      }
      output.add(map);
    }
  }

  private static void formatStatusToOutput(List<Map<String, JsonElement>> output) {}

  private static void formatCollectorExpectationToOutput(List<Map<String, JsonElement>> output) {}

  private static void formatExpirationManagerToOutput(List<Map<String, JsonElement>> output) {}

  private static void formatManualUpdateToOutput(List<Map<String, JsonElement>> output) {}

  /**
   * Builds a map of output field names to their contract types from the inject's payload or
   * contract.
   */
  private Map<String, ContractOutputType> buildFieldTypeMapFromInject(Inject inject) {
    Map<String, ContractOutputType> fieldTypeMap = new HashMap<>();
    if (inject.getPayload().isPresent()) {
      Set<OutputParser> outputParsers = structuredOutputUtils.extractOutputParsers(inject);
      injectorContractContentUtils
          .getAllContractOutputs(outputParsers)
          .forEach(out -> fieldTypeMap.put(out.getKey(), out.getType()));
    } else {
      if (inject.getInjectorContract().isPresent()) {
        injectorContractContentUtils
            .getAllContractOutputs(inject.getInjectorContract().get())
            .forEach(out -> fieldTypeMap.put(out.getField(), out.getType()));
      }
    }
    return fieldTypeMap;
  }
}
