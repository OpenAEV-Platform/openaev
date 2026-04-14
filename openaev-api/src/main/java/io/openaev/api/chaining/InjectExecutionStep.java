package io.openaev.api.chaining;

import static io.openaev.database.model.Command.COMMAND_TYPE;
import static io.openaev.database.model.DnsResolution.DNS_RESOLUTION_TYPE;
import static io.openaev.database.model.Executable.EXECUTABLE_TYPE;
import static io.openaev.database.model.FileDrop.FILE_DROP_TYPE;
import static io.openaev.service.chaining.StepService.setField;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.*;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Executor;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.*;
import io.openaev.service.chaining.StepService;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.TargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
  private static final Gson gson = new Gson();
  private final InjectorContractService injectorContractService;
  private final UserService userService;
  private final AssetService assetService;
  private final TeamService teamService;
  private final TagService tagService;
  private final DocumentService documentService;
  private final InjectService injectService;
  private final TagRuleService tagRuleService;
  private final AssetGroupService assetGroupService;
  private final InjectorContractContentUtils injectorContractContentUtils;
  private final Executor executor;
  private final InjectUtils injectUtils;
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
    if (data == null)
      throw new ChainingException(
          "New step (TEMPLATE): Error processing Inject. Workflow has no simulation or scenario");

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
    Inject inject = injectService.findInjectOrNull(injectId);
    if (inject == null)
      throw new ChainingException(
          "Inject not found. ID: " + injectId,
          new ElementNotFoundException("Inject not found. ID: " + injectId));

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

      stepRun.setOutput(jsonObject.toString());
      return Optional.of(stepRun);
    }

    log.info("Inject output not found. ID:  {}", injectId);
    return Optional.empty();
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

    if (data == null)
      throw new IllegalArgumentException("Data step of new step (TEMPLATE) is null");

    if (data.getInjectorContract() == null)
      throw new IllegalArgumentException(
          "Data step of new step (TEMPLATE) do not contain injector contract");

    if ((simulation == null && scenario == null) || (simulation != null && scenario != null))
      throw new IllegalArgumentException("Exactly one of exercise or scenario should be present");

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
    if (conditions == null || conditions.isEmpty()) return "{}";
    List<Map<String, Object>> inputs = new ArrayList<>();

    for (ConditionCreateInput condition : conditions) {
      if (ConditionType.MAPPER.equals(condition.getType())) {

        Map<String, Object> input = new HashMap<>();
        input.put("key", condition.getKey());
        input.put("keyType", condition.getKeyType() != null ? condition.getKeyType().name() : null);
        input.put("path", condition.getValue());
        input.put("id_step_from", condition.getStepFrom());

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
   * @return list of step create inputs
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
   *     the injector or contract is missing or if an exception occurs during deserialization
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
      InjectorContract injectorContract = inject.getInjectorContract().get();
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

      return inject;

    } catch (JsonProcessingException e) {
      throw new ChainingException("Step (READY) : Error processing JSON to Inject ", e);
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
  private static void formatExecutionTracesToOutput(
      InjectStatus injectStatus, List<Map<String, JsonElement>> output) {
    // GET EXECUTION TRACE
    List<ExecutionTrace> traces = injectStatus.getTraces();
    for (ExecutionTrace trace : traces) {
      Map<String, JsonElement> map = new HashMap<>();
      if (trace.getAgent() == null) continue;
      map.put("agent_id", gson.toJsonTree(trace.getAgent().getId()));
      if (trace.getStructuredOutput() != null) {
        map.put("parsed", gson.toJsonTree(trace.getStructuredOutput()));
      } else {
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
}
