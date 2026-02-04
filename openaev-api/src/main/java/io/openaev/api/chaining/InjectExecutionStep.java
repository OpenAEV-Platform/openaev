package io.openaev.api.chaining;

import static io.openaev.service.chaining.StepService.setField;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.*;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Executor;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.*;
import io.openaev.service.chaining.StepService;
import io.openaev.utils.TargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link ActionStep} for executing Inject steps.
 *
 * <p>Handles creation, waiting, running, updating, and ending of steps that use the {@link
 * StepActionClass#INJECT_EXECUTION} action.
 *
 * <p>Responsible for:
 *
 * <ul>
 *   <li>Creating step templates and wait steps
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
  private final InjectStatusService injectStatusService;
  @PersistenceContext private EntityManager em;

  /**
   * Creates a new step template for an inject execution.
   *
   * @param newStep the new step from front
   * @param workflow the workflow template this step belongs to
   * @return a step in TEMPLATE status
   */
  @Override
  public Step create(StepsCreateInput.StepCreateInput newStep, Workflow workflow) {
    String data = this.stepData(newStep, workflow.getSimulation());

    if (data == null) throw new IllegalArgumentException("Data step is null");

    String input = stepInputFromConditionMapper(newStep.getConditions());
    // TODO: get outputParser
    String outputParser = this.stepOutputParser("");
    return Step.builder()
        .data(data)
        .input(input)
        .output_parser(outputParser)
        .status(StepStatus.TEMPLATE)
        .stepAction(StepActionClass.INJECT_EXECUTION)
        .limitExecution(newStep.getLimitExecution())
        .workflow(workflow)
        .build();
  }

  /**
   * Creates a Wait step from a step template.
   *
   * <p>The step is initialized in WAIT status and contains the same data as the template.
   *
   * @param stepTemplate the template step to duplicate
   * @param input the input to apply for this execution
   * @param workflowRun the workflow run this step belongs to
   * @return a step in WAIT status ready to be executed
   */
  @Override
  public Step wait(Step stepTemplate, String input, Workflow workflowRun) {
    // CALL BY when new input or start simulation
    Step waitStep = new Step();
    waitStep.setWorkflow(workflowRun);
    waitStep.setData(stepTemplate.getData());
    waitStep.setStepTemplate(stepTemplate);
    // TODO manage input from output paser from payload or nuclei or nmap
    waitStep.setInput(input);
    waitStep.setStatus(StepStatus.READY);
    waitStep.setStepAction(StepActionClass.INJECT_EXECUTION);
    waitStep.setLimitExecution(stepTemplate.getLimitExecution());

    return waitStep;
  }

  /**
   * Runs a WAIT step by executing the corresponding to inject.
   *
   * <p>Handles deserialization of step data, creation of the inject, execution via {@link
   * Executor}, and updates the step data with inject ID.
   *
   * @param waitStep the step currently in WAIT status
   * @return the updated step with execution info, or null if execution fails
   */
  @Override
  public Step run(Step waitStep) {
    // CALL BY QUEUE WAIT
    ObjectMapper om =
        new ObjectMapper()
            .findAndRegisterModules()
            .setInjectableValues(new InjectableValues.Std().addValue(EntityManager.class, em))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    try {
      // GET INJECT FROM JSON
      Inject inject = om.readValue(waitStep.getData(), Inject.class);
      JsonNode root = om.readTree(waitStep.getData());

      JsonNode injectorNode =
          root.path("inject_injector_contract").path("injector_contract_injector");

      // Get injector contract by id, cause cannot be serialized:
      if (!injectorNode.isMissingNode()) {
        String injectorId = injectorNode.asText();

        Injector injector = em.find(Injector.class, injectorId);
        Optional<InjectorContract> injectorContract = inject.getInjectorContract();
        if (injectorContract.isPresent()) {
          injectorContract.get().setInjector(injector);
        } else {
          log.info(
              "Injector contract not found for injectorId {} & step (WAIT) id {}",
              injectorId,
              waitStep.getId());
        }
      }
      // CREATE & SAVE INJECT
      inject = injectService.createInject(inject);

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

      try {
        executor.directExecute(executableInject);
        String data = setInjectId(inject.getId(), waitStep.getData());
        waitStep.setData(data);
        return waitStep;
      } catch (Exception e) {
        log.warn(e.getMessage(), e);
        injectStatusService.failInjectStatus(inject.getId(), e.getMessage());
      }
    } catch (JsonProcessingException e) {
      log.warn(e.getMessage(), e);
      return null;
    }
    return null;
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
  public Step update(Step stepRun) {
    // GET INJECT
    String data = stepRun.getData();
    String injectId = StepService.getField(data, "inject_id");
    Inject inject = injectService.findInjectOrNull(injectId);
    if (inject == null) {
      log.warn("inject not found for injectId {}", injectId);
      return null;
    }

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
      return stepRun;
    }

    log.warn("inject status not found for injectId {}", injectId);
    return null;
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
   * exercise data.
   *
   * <p>If the inject content is missing, default values are loaded from the injector contract.
   *
   * @param step the step creation input containing the inject definition
   * @param exercise the exercise context, if any
   * @return a JSON string representing the serialized inject, or {@code null} if the injector
   *     contract is missing
   */
  private String stepData(StepsCreateInput.StepCreateInput step, Exercise exercise) {

    InjectInput data = (InjectInput) step.getDataStep();

    if (data.getInjectorContract() == null) {
      log.warn("injector contract not found for step create input {}", step);
      return null;
    }

    InjectorContract injectorContract =
        this.injectorContractService.injectorContract(data.getInjectorContract());
    Inject inject = data.toInject(injectorContract);
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
    // TODO Scenario or EXERCISE copy from io/openaev/rest/inject/service/InjectService.java:178
    if (exercise != null) {
      tags = exercise.getTags();
      inject.setExercise(exercise);
      // Linked documents directly to the exercise
      inject
          .getDocuments()
          .forEach(
              document -> {
                if (!document.getDocument().getExercises().contains(exercise)) {
                  exercise.getDocuments().add(document.getDocument());
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
      throw new IllegalArgumentException("Error processing Inject to JSON");
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
   *   <li>{@code id_step_from} – the source step ID
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
   * Converts an {@link InjectInput} into a list of {@link StepsCreateInput.StepCreateInput}.
   *
   * @param input the inject input
   * @return list of step create inputs
   */
  public static StepsCreateInput.StepCreateInput getInjectAsStepsCreateInput(InjectInput input) {
    StepsCreateInput.StepCreateInput stepCreateInput = new StepsCreateInput.StepCreateInput();
    stepCreateInput.setDataStep(input);
    stepCreateInput.setStepAction(StepActionClass.INJECT_EXECUTION);
    stepCreateInput.setLimitExecution(1);

    if (input.getDependsDuration() != 0) {
      ConditionCreateInput conditionCreateInput =
          ConditionCreateInput.builder()
              .temporaryId("0")
              .type(ConditionType.AFTER)
              .key(null)
              .value(String.valueOf(input.getDependsDuration()))
              .build();
      stepCreateInput.setConditions(List.of(conditionCreateInput));
    }

    return stepCreateInput;
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
