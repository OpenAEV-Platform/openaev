package io.openaev.api.chaining;

import static io.openaev.database.model.Command.COMMAND_TYPE;
import static io.openaev.database.model.DnsResolution.DNS_RESOLUTION_TYPE;
import static io.openaev.database.model.Executable.EXECUTABLE_TYPE;
import static io.openaev.database.model.FileDrop.FILE_DROP_TYPE;
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
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.rest.tag.TagService;
import io.openaev.service.*;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService;
import io.openaev.service.attackpath.ingestion.AttackPathFindingIngestionService;
import io.openaev.service.attackpath.ingestion.AttackPathVerdictSyncService;
import io.openaev.service.chaining.ConditionService;
import io.openaev.service.chaining.ScopeService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowStateService;
import io.openaev.utils.ConditionUtils;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.TargetType;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import jakarta.annotation.Resource;
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
  private final ScopeService scopeService;
  private final PreviewFeatureService previewFeatureService;
  private final AttackPathExecutionIngestionService attackPathIngestion;
  private final AttackPathFindingIngestionService attackPathFindingIngestion;
  private final AttackPathVerdictSyncService attackPathVerdictSync;
  private final InjectExpectationService injectExpectationService;

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
    readyStep.setStepTemplate(stepTemplate);
    // TODO manage input from output paser from payload or nuclei or nmap
    readyStep.setInput(input);
    readyStep.setStatus(StepStatus.READY);
    readyStep.setStepAction(StepActionClass.INJECT_EXECUTION);
    readyStep.setLimitExecution(stepTemplate.getLimitExecution());

    // Bake the per-target information (produced by expandTargetBatches) into the step data so
    // that run() can execute a single pre-configured inject without re-resolving scope.
    readyStep.setData(bakeTargetIntoStepData(stepTemplate.getData(), input));

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
    InjectorContract injectorContract =
        inject
            .getInjectorContract()
            .orElseThrow(
                () ->
                    new ChainingException(
                        "Injector contract missing on inject during run, step ID: "
                            + readyStep.getId()));
    prepareGetStatusPayloadFromInject(injectorContract);

    recordAttackPathExecution(readyStep, inject);

    try {
      readyStep.setData(setInjectId(injectId, readyStep.getData()));
      ExecutableInject executableInject =
          new ExecutableInject(
              true,
              true,
              inject,
              inject.getTeams(),
              inject.getAssets(),
              inject.getAssetGroups(),
              List.of(),
              true);

      // TODO Check add documents? Executable Payloads
      // executableInject.addDirectAttachment(inject.getDocuments());

      executor.directExecute(executableInject);

      return Optional.of(readyStep);
    } catch (Exception e) {
      throw new ChainingException(
          "Inject execution failed. Inject ID: " + injectId + " (transaction rolled back)", e);
    }
  }

  /**
   * Records the run's attack-path rows: flag-gated and non-fatal. The whole ingestion (resolution +
   * write) runs inside {@code onRun}, which opens its own tenant-scoped REQUIRES_NEW transaction,
   * so a failure here is caught and logged and can never roll the inject execution back. Recover
   * around this boundary, never inside {@code onRun} (activate-tenant-table skill).
   */
  private void recordAttackPathExecution(Step readyStep, Inject inject) {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.ATTACK_PATH)) {
      return;
    }
    try {
      attackPathIngestion.onRun(inject, readyStep, this.getCommand(inject));
    } catch (Exception e) {
      log.warn("Attack-path ingestion skipped for inject {} (non-fatal)", inject.getId(), e);
    }
  }

  /**
   * Copies the inject's findings onto the attack-path snapshot: flag-gated and non-fatal. The copy
   * opens its own tenant-scoped REQUIRES_NEW transaction, so a failure here is caught and logged
   * and can never roll the step update back. Runs on every execution event; the copy is idempotent.
   */
  private void recordAttackPathFindings(Step stepRun, Inject inject) {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.ATTACK_PATH)) {
      return;
    }
    try {
      attackPathFindingIngestion.copyFindings(inject, stepRun);
    } catch (Exception e) {
      log.warn("Attack-path findings copy skipped for inject {} (non-fatal)", inject.getId(), e);
    }
  }

  /**
   * Pushes the step's expectation verdicts (prevention / detection / vulnerability) onto the
   * attack-path projection: flag-gated and non-fatal, like the two ingestion hooks above. The sync
   * opens its own tenant-scoped REQUIRES_NEW transaction, so a failure here is caught and logged
   * and can never roll the step update back. Runs on every execution event; the writes are guarded,
   * so replaying the same results changes nothing.
   */
  private void syncAttackPathVerdicts(
      Step stepRun, Inject inject, List<BaseInjectExpectation> expectations) {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.ATTACK_PATH)) {
      return;
    }
    try {
      attackPathVerdictSync.sync(stepRun, inject, expectations);
    } catch (Exception e) {
      log.warn("Attack-path verdict sync skipped for inject {} (non-fatal)", inject.getId(), e);
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

    if (injectId == null || injectId.isBlank()) {
      log.info("No inject ID found for step ID: {}", stepRun.getId());
      return Optional.empty();
    }

    Inject inject = injectService.inject(injectId);

    // COPY FINDINGS onto the attack-path snapshot (per event, idempotent, non-fatal)
    recordAttackPathFindings(stepRun, inject);

    // GET INJECT STATUS
    InjectStatus injectStatus = inject.getStatus().orElse(null);

    List<Map<String, JsonElement>> output = new ArrayList<>();
    if (injectStatus != null) {
      // FORMAT EXECUTION TRACE TO OUTPUT STEP
      formatExecutionTracesToOutput(injectStatus, output);
      // FORMAT INJECT STATUS TO OUTPUT STEP
      formatStatusToOutput(inject, output);
    }

    // FORMAT EXPECTATION TO OUTPUT STEP
    List<BaseInjectExpectation> expectations = formatExpectationToOutput(inject, output);

    // SYNC the same verdicts onto the attack-path projection (per event, idempotent, non-fatal)
    syncAttackPathVerdicts(stepRun, inject, expectations);

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

    log.info("[Chaining] Inject output not found. ID:  {}", injectId);
    return Optional.empty();
  }

  /**
   * Syncs parsed execution output into workflow global state, potentially triggering chained steps.
   */
  private void processOutputAndStateSync(
      Step stepRun, List<Map<String, JsonElement>> output, Inject inject) {
    JsonObject outputData = extractDataFromParsed(output);
    if (outputData.isEmpty()) {
      return;
    }

    Workflow workflowRun = stepRun.getWorkflow();
    Map<String, ChainingMappedType> outputTypeMappings = buildTypeMappingsFromInject(inject);
    // Sync global state with execution output values mapped to chaining primitive/complex types.
    workflowStateService.syncState(outputData, outputTypeMappings, workflowRun);
  }

  /** Extracts structured "parsed" output entries into normalized arrays by key. */
  private JsonObject extractDataFromParsed(List<Map<String, JsonElement>> output) {
    JsonObject result = new JsonObject();

    for (Map<String, JsonElement> entry : output) {
      JsonElement parsedElement = entry.get("parsed");
      if (parsedElement == null || !parsedElement.isJsonObject()) {
        continue;
      }

      JsonObject parsed = parsedElement.getAsJsonObject();
      for (Map.Entry<String, JsonElement> parsedEntry : parsed.entrySet()) {
        addParsedValues(result, parsedEntry.getKey(), parsedEntry.getValue());
      }
    }
    return result;
  }

  private void addParsedValues(JsonObject result, String key, JsonElement parsedValueElement) {
    if (parsedValueElement == null || parsedValueElement.isJsonNull()) {
      return;
    }
    JsonArray target =
        result.has(key) && result.get(key).isJsonArray()
            ? result.getAsJsonArray(key)
            : new JsonArray();
    if (!result.has(key)) {
      result.add(key, target);
    }
    if (parsedValueElement.isJsonArray()) {
      for (JsonElement item : parsedValueElement.getAsJsonArray()) {
        addParsedValue(target, item);
      }
      return;
    }
    addParsedValue(target, parsedValueElement);
  }

  private void addParsedValue(JsonArray target, JsonElement item) {
    if (item == null || item.isJsonNull()) {
      return;
    }
    target.add(item.deepCopy());
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

    Injector injector = injectUtils.resolveInjector(data.getInjectorId(), injectorContract);
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
      inject.setContent(resolveBaseInjectContent(inject, injectorContract));
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
   * Expands condition batches into per-target batches by resolving scope assets and IPs.
   *
   * <p>Each original batch is duplicated once per in-scope asset and once per in-scope IP. The
   * target is embedded in the batch {@code inputString} under {@code "_target"} so that {@link
   * #ready} can bake it into the step data for {@link #run} to consume.
   */
  public List<ConditionService.ExecutionBatch> expandTargetBatches(
      List<ConditionService.ExecutionBatch> batches, Workflow workflowRun) {

    String workflowId = workflowRun.getId();
    List<Asset> validAssets = scopeService.getValidAssets(workflowId);
    List<String> validManualTargets = scopeService.getValidManualTargetsFromScope(workflowId);

    if (validAssets.isEmpty() && validManualTargets.isEmpty()) {
      return batches;
    }

    // For each condition batch, produce one new batch per target (asset or IP).
    // Each batch becomes one READY step → one inject → one execution unit.
    // The target is embedded in the inputString under "_target" so that ready() can
    // persist it into step.data, and getInjectFromDataStep() can apply it at run time.
    List<ConditionService.ExecutionBatch> expanded = new ArrayList<>();
    for (ConditionService.ExecutionBatch batch : batches) {
      // Deduplicate assets that may appear via multiple scope rules
      Map<String, Asset> uniqueAssets = new LinkedHashMap<>();
      validAssets.forEach(a -> uniqueAssets.put(a.getId(), a));

      // One batch per asset (denylist already applied by ScopeService)
      for (Asset asset : uniqueAssets.values()) {
        JsonObject target = new JsonObject();
        target.addProperty("type", "ASSET");
        target.addProperty("assetId", asset.getId());
        expanded.add(
            new ConditionService.ExecutionBatch(
                addTargetToInput(batch.inputString(), target), batch.usedMappers(), null));
      }

      // One batch per manual data
      Set<String> uniqueManualTargets = new LinkedHashSet<>(validManualTargets);
      for (String manualTarget : uniqueManualTargets) {
        JsonObject target = new JsonObject();
        target.addProperty("type", "MANUAL");
        target.addProperty("manual", manualTarget);
        expanded.add(
            new ConditionService.ExecutionBatch(
                addTargetToInput(batch.inputString(), target), batch.usedMappers(), null));
      }
    }
    return expanded;
  }

  /**
   * Returns {@code true} if the step template data contains a non-null payload inside its injector
   * contract ({@code inject_injector_contract.injector_contract_payload}).
   */
  public boolean hasPayload(Step stepTemplate) {
    if (stepTemplate.getData() == null) {
      return false;
    }
    try {
      JsonElement contractElement =
          JsonParser.parseString(stepTemplate.getData())
              .getAsJsonObject()
              .get("inject_injector_contract");
      if (contractElement == null
          || contractElement.isJsonNull()
          || !contractElement.isJsonObject()) {
        return false;
      }
      JsonElement payloadElement =
          contractElement.getAsJsonObject().get("injector_contract_payload");
      if (payloadElement == null || payloadElement.isJsonNull() || !payloadElement.isJsonObject()) {
        return false;
      }

      JsonElement payloadType = payloadElement.getAsJsonObject().get("payload_type");
      return payloadType != null && !payloadType.isJsonNull();
    } catch (Exception e) {
      log.warn(
          "Failed to check payload presence in step data for step ID: {}", stepTemplate.getId());
      return false;
    }
  }

  /** Embeds a {@code "_target"} entry into the batch input JSON. */
  private String addTargetToInput(String inputJson, JsonObject target) {
    JsonObject input =
        (inputJson == null || inputJson.isBlank())
            ? new JsonObject()
            : JsonParser.parseString(inputJson).getAsJsonObject();
    input.add("_target", target);
    return input.toString();
  }

  private String bakeTargetIntoStepData(String templateData, String input) {
    if (input == null || input.isBlank()) {
      return templateData;
    }
    try {
      JsonObject parsedInput = JsonParser.parseString(input).getAsJsonObject();
      JsonElement targetElement = parsedInput.get("_target");
      if (targetElement == null || targetElement.isJsonNull()) {
        return templateData;
      }
      JsonObject dataObj = JsonParser.parseString(templateData).getAsJsonObject();
      dataObj.add("_chaining_target", targetElement);
      return dataObj.toString();
    } catch (Exception e) {
      log.warn("Failed to bake target into step data, falling back to template data", e);
      return templateData;
    }
  }

  /**
   * Reads {@code "_chaining_target"} from step data and applies it to the inject before execution.
   *
   * <ul>
   *   <li>ASSET: sets a single asset and, for external injectors, rewrites the content target
   *       fields (targets_assets, target_selector, targets/IPs).
   *   <li>MANUAL: sets manual targeting in content and clears asset fields.
   *   <li>No target present: inject runs with its template defaults.
   * </ul>
   *
   * @return {@code true} if a target was found and applied, {@code false} otherwise
   */
  private boolean applyChainingTarget(Inject inject, String stepData, boolean isExternalInjector) {
    if (stepData == null) {
      return false;
    }
    JsonElement targetElement;
    try {
      targetElement = JsonParser.parseString(stepData).getAsJsonObject().get("_chaining_target");
    } catch (Exception e) {
      log.warn("Failed to read _chaining_target from step data", e);
      return false;
    }
    if (targetElement == null || targetElement.isJsonNull() || !targetElement.isJsonObject()) {
      return false;
    }

    JsonObject target = targetElement.getAsJsonObject();
    String type = target.has("type") ? target.get("type").getAsString() : null;

    if ("ASSET".equals(type)) {
      String assetId = target.has("assetId") ? target.get("assetId").getAsString() : null;
      if (assetId == null) {
        return false;
      }
      Asset asset = assetService.asset(assetId);
      inject.setAssets(List.of(asset));
      inject.setAssetGroups(List.of());
      if (isExternalInjector) {
        ObjectNode content =
            inject.getContent() != null
                ? inject.getContent().deepCopy()
                : mapper.createObjectNode();
        content.remove("targets");
        content.remove("targets_assets");
        content.remove("target_selector");
        applyScopeTargetsToContent(content, List.of(asset));
        inject.setContent(content);
      }
      return true;
    } else if ("MANUAL".equals(type)) {
      String manualTarget = target.has("manual") ? target.get("manual").getAsString() : null;
      if (manualTarget == null) {
        return false;
      }
      ObjectNode content =
          inject.getContent() != null ? inject.getContent().deepCopy() : mapper.createObjectNode();
      content.put("target_selector", "manual");
      content.put("targets", manualTarget);
      content.remove("targets_assets");
      inject.setContent(content);
      inject.setAssets(List.of());
      inject.setAssetGroups(List.of());
      return true;
    }
    return false;
  }

  /** Stores the created inject ID into the step data JSON. */
  private String setInjectId(String injectId, String dataStep) {
    JsonObject jsonObject = JsonParser.parseString(dataStep).getAsJsonObject();
    jsonObject.addProperty("inject_id", injectId);
    return jsonObject.toString();
  }

  private String getCommand(Inject inject) {
    if (inject.getStatus().isEmpty()) return "";

    InjectStatus status = inject.getStatus().get();
    StatusPayload statusPayload = status.getPayloadOutput();
    if (statusPayload == null || statusPayload.getPayloadCommandBlocks() == null) {
      return "";
    }
    StringBuilder command = new StringBuilder();
    statusPayload
        .getPayloadCommandBlocks()
        .forEach(
            payloadCommandBlock -> {
              command.append(payloadCommandBlock.getContent());
              command.append("\n");
            });
    return command.toString();
  }

  private Map<String, StringBuilder> getExecutionTracesByEndpointIndex(Inject inject) {
    Map<String, StringBuilder> tracesByEndpointSource = new HashMap<>();
    if (inject.getStatus().isEmpty()) return tracesByEndpointSource;

    InjectStatus status = inject.getStatus().get();
    List<ExecutionTrace> executionTraces = status.getTraces();

    if (inject.getInjector() == null) {
      executionTraces.forEach(
          executionTrace -> {
            if (executionTrace.getAgent() == null || executionTrace.getAgent().getAsset() == null) {
              return;
            }
            String agentId =
                executionTrace.getAgent().getId() + executionTrace.getAgent().getAsset().getId();
            StringBuilder agentTraces =
                tracesByEndpointSource.computeIfAbsent(agentId, k -> new StringBuilder());
            // A trace with no timestamp must not render a literal "null" at the start of the line.
            if (executionTrace.getTime() != null) {
              agentTraces.append(executionTrace.getTime()).append(" ");
            }
            agentTraces
                .append(executionTrace.getStatus().name())
                .append(" ")
                .append(executionTrace.getMessage())
                .append("\n");
          });
    } else {
      // TODO BUILD INDEX
      String injectorId = inject.getInjector().getId();
      executionTraces.forEach(
          executionTrace -> {
            StringBuilder injectorTraces =
                tracesByEndpointSource.computeIfAbsent(injectorId, k -> new StringBuilder());
            // A trace with no timestamp must not render a literal "null" at the start of the line.
            if (executionTrace.getTime() != null) {
              injectorTraces.append(executionTrace.getTime()).append(" ");
            }
            injectorTraces
                .append(executionTrace.getStatus().name())
                .append(" ")
                .append(executionTrace.getMessage())
                .append("\n");
          });
    }
    return tracesByEndpointSource;
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

      // Ensure dependsDuration has a default value — it is a legacy scheduling field
      // that may not be present in the serialized step data but has a @NotNull constraint.
      if (inject.getDependsDuration() == null) {
        inject.setDependsDuration(0L);
      }

      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(step.getData());

      // GET INJECTOR CONTRACT
      InjectorContract injectorContract =
          inject
              .getInjectorContract()
              .map(
                  contract -> {
                    try {
                      Hibernate.initialize(contract);
                    } catch (Exception e) {
                      throw new RuntimeException(
                          "Injector contract not found for step (READY) ID: " + step.getId(), e);
                    }
                    return injectorContractRepository
                        .findById(contract.getId())
                        .orElseThrow(
                            () ->
                                new RuntimeException(
                                    "Injector contract not found for step (READY) ID: "
                                        + step.getId()));
                  })
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
        injector.setTenantId(injectorContract.getTenant().getId());
        inject.setInjector(injector);
      }

      // Modify payload arguments with inputs from step
      ObjectNode updatedContent =
          updateContentWithInputs(step, resolveBaseInjectContent(inject, injectorContract));

      // Add expectations
      ObjectNode contentWithExpectations =
          injectorContractContentUtils.setExpectations(injectorContract, updatedContent);
      inject.setContent(contentWithExpectations);

      // Apply the per-target info baked in by ready() (asset or IP from scope).
      // For injector-based steps, expansion set _chaining_target → one asset per step.
      // For payload-based steps (no expansion), fall back to all scope assets so the
      // executor can distribute the payload across every scoped endpoint — same behavior
      // as before expandTargetBatches was introduced.
      boolean targetApplied =
          applyChainingTarget(inject, step.getData(), injectorContract.getPayload() == null);
      if (!targetApplied && step.getWorkflow() != null) {
        List<Asset> scopedAssets = scopeService.getValidAssets(step.getWorkflow().getId());
        if (scopedAssets != null && !scopedAssets.isEmpty()) {
          inject.setAssets(scopedAssets);
        }
      }

      return inject;

    } catch (JsonProcessingException e) {
      throw new ChainingException("Step (READY) : Error processing JSON to Inject ", e);
    }
  }

  /**
   * Updates the inject content JSON with scope-resolved asset targets.
   *
   * <p>Sets {@code targets_assets} (asset IDs) for asset-aware injectors, {@code target_selector}
   * to {@code "assets"}, and {@code targets} with the comma-separated IPs extracted from scoped
   * {@link Endpoint} assets so that IP-based injectors (nmap, nuclei) also receive valid targets.
   */
  private void applyScopeTargetsToContent(ObjectNode content, List<Asset> scopedAssets) {
    if (scopedAssets.isEmpty()) {
      return;
    }
    List<String> assetIds = scopedAssets.stream().map(Asset::getId).toList();
    content.set("targets_assets", mapper.valueToTree(assetIds));
    content.put("target_selector", "assets");

    List<String> ips = new ArrayList<>();
    for (Asset asset : scopedAssets) {
      if (asset instanceof Endpoint endpoint) {
        if (endpoint.getIps() != null && endpoint.getIps().length > 0) {
          ips.addAll(Arrays.asList(endpoint.getIps()));
        }
        if (endpoint.getSeenIp() != null && !endpoint.getSeenIp().isEmpty()) {
          ips.add(endpoint.getSeenIp());
        }
      }
    }
    List<String> distinctIps =
        ips.stream().filter(ip -> ip != null && !ip.isEmpty()).distinct().toList();
    if (!distinctIps.isEmpty()) {
      content.put("targets", String.join(",", distinctIps));
    }
  }

  /**
   * Merges step input values into injector contract content to build runtime payload arguments.
   *
   * <p>This method starts from the resolved base inject content, fetches input values resolved
   * during chaining from {@link Step#getInput()}, then maps those values to contract keys using
   * MAPPER conditions. The resulting JSON is used as inject content for execution.
   *
   * @param step the READY step containing resolved input values used as payload arguments
   * @param baseContent base inject content JSON
   * @return updated contract content with mapped input values injected; empty object if parsing
   *     fails
   */
  private ObjectNode updateContentWithInputs(Step step, ObjectNode baseContent) {
    if (baseContent == null) {
      return mapper.createObjectNode();
    }

    try {
      ObjectNode contentNode = baseContent.deepCopy();

      String inputJson = step.getInput();
      if (inputJson == null || inputJson.isEmpty() || "{}".equals(inputJson)) {
        return contentNode;
      }

      JsonNode inputValues = mapper.readTree(inputJson);

      conditionService.findAllConditionsByStepId(step.getId()).stream()
          .filter(conditionUtils::isMapperCondition)
          .forEach(mapping -> applyMapping(contentNode, mapping, inputValues));

      return contentNode;

    } catch (JsonProcessingException e) {
      return mapper.createObjectNode();
    }
  }

  private ObjectNode resolveBaseInjectContent(Inject inject, InjectorContract injectorContract) {
    if (inject != null && inject.getContent() != null && !inject.getContent().isEmpty()) {
      return inject.getContent();
    }
    ObjectNode defaults =
        injectorContractContentUtils.getDynamicInjectorContractFieldsForInject(injectorContract);
    return defaults != null ? defaults : mapper.createObjectNode();
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
    if (mapping.getKeyType() == null) {
      log.warn("[Chaining] Skipping mapper condition {} because keyType is null", mapping.getId());
      return;
    }
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
        // Network injectors (nmap, netexec, …) execute without an on-host agent, so their traces
        // carry no
        // agent — but they DO carry the structured output the chaining engine decomposes into
        // primitives
        // to drive events. Only the agent_id enrichment is agent-specific. Dropping the whole trace
        // here
        // meant no port/share/… event could ever fire from a network injector's findings; keep the
        // structured output so it still feeds the workflow state.
        if (trace.getStructuredOutput() != null) {
          map.put("parsed", JsonParser.parseString(trace.getStructuredOutput().toString()));
          output.add(map);
        }
        continue;
      }
      map.put("agent_id", gson.toJsonTree(trace.getAgent().getId()));
      if (trace.getStructuredOutput() != null) {
        log.info("[Chaining] Trace has structuredOutput: {}", trace.getStructuredOutput());
        map.put("parsed", JsonParser.parseString(trace.getStructuredOutput().toString()));
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

  /**
   * Formats the inject execution status (name + tracking dates) into the step output.
   *
   * @param inject the inject whose status is read
   * @param output the output list to populate
   */
  private static void formatStatusToOutput(Inject inject, List<Map<String, JsonElement>> output) {
    inject
        .getStatus()
        .ifPresent(
            status -> {
              if (status.getName() == null || status.getTrackingEndDate() == null) return;
              Map<String, JsonElement> map = new HashMap<>();
              map.put(
                  "inject_status",
                  gson.toJsonTree(status.getName() != null ? status.getName().name() : null));
              map.put(
                  "inject_status_tracking_end_date",
                  gson.toJsonTree(
                      status.getTrackingEndDate() != null
                          ? status.getTrackingEndDate().toString()
                          : null));
              output.add(map);
            });
  }

  /**
   * Formats expectations updated by an collector or expiration or manual update into the step
   * output. Each entry contains the expectation type, name, score, collector source ID, and — when
   * the expectation is agent-level — the associated agent ID and asset ID.
   *
   * <p>Some expectations may not match an actual execution. We observed duplicate expectation
   * entries returned without an agent_id and only with an asset_id. These entries do not correspond
   * to real executions; they are duplicated information.
   *
   * @param inject the inject already loaded by the caller
   * @param output the output list to populate
   * @return the inject's expectations, so the caller can sync the same verdicts elsewhere without a
   *     second read
   */
  private List<BaseInjectExpectation> formatExpectationToOutput(
      Inject inject, List<Map<String, JsonElement>> output) {
    List<BaseInjectExpectation> expectations =
        injectExpectationService.findAllByInjectId(inject.getId());
    for (BaseInjectExpectation expectation : expectations) {
      for (InjectExpectationResult result : expectation.getResults()) {
        Map<String, JsonElement> map = getExpectationOutput(expectation, result);
        addEndpointContext(inject, expectation, map);
        output.add(map);
      }
    }
    return expectations;
  }

  /**
   * Enriches an output map entry with endpoint context (agent_id, asset_id, asset_group_id, plus
   * normalised target_type / target_id) when the expectation is a {@link
   * TechnicalInjectExpectation} (i.e. PREVENTION or DETECTION).
   *
   * <p>Granularity priority:
   *
   * <ol>
   *   <li>Agent-level: {@code agent != null} → target_type=AGENT, target_id=agent.id
   *   <li>Asset-level (no agent): {@code asset != null} → target_type=ASSET, target_id=asset.id
   *   <li>Asset-group-level: {@code assetGroup != null} → target_type=ASSET_GROUP,
   *       target_id=assetGroup.id
   * </ol>
   *
   * @param inject
   * @param expectation the expectation to inspect
   * @param map the output map to enrich
   */
  private static void addEndpointContext(
      Inject inject, BaseInjectExpectation expectation, Map<String, JsonElement> map) {
    if (!(expectation instanceof TechnicalInjectExpectation technical)) {
      return;
    }
    Agent agent = technical.getAgent();
    Asset asset = technical.getAsset();
    AssetGroup assetGroup = technical.getAssetGroup();

    map.put("agent_id", gson.toJsonTree(agent != null ? agent.getId() : null));
    map.put("asset_id", gson.toJsonTree(asset != null ? asset.getId() : null));
    map.put("asset_name", gson.toJsonTree(asset != null ? asset.getName() : null));
    map.put("asset_group_id", gson.toJsonTree(assetGroup != null ? assetGroup.getId() : null));

    // Normalised target resolution for PREVENTION / DETECTION:
    // the chaining engine uses target_type + target_id to correlate the result
    // to the right scope without having to inspect every nullable field.
    String targetType;
    String targetId;
    if (agent != null) {
      targetType = "AGENT";
      targetId = agent.getId();
    } else if (asset != null) {
      targetType = "ASSET";
      targetId = asset.getId();
    } else if (assetGroup != null) {
      targetType = "ASSET_GROUP";
      targetId = assetGroup.getId();
    } else { // TODO
      targetType = null;
      targetId = null;
    }
    map.put("target_type", gson.toJsonTree(targetType));
    map.put("target_id", gson.toJsonTree(targetId));
  }

  private static Map<String, JsonElement> getExpectationOutput(
      BaseInjectExpectation expectation, InjectExpectationResult result) {
    Map<String, JsonElement> map = new HashMap<>();
    map.put("expectation_id", gson.toJsonTree(expectation.getId()));
    map.put(
        "expectation_type",
        gson.toJsonTree(expectation.getType() != null ? expectation.getType().name() : null));
    map.put("expectation_name", gson.toJsonTree(expectation.getName()));
    map.put("expectation_score", gson.toJsonTree(expectation.getScore()));
    map.put("source_type", gson.toJsonTree(result.getSourceType()));
    map.put("source_name", gson.toJsonTree(result.getSourceName()));
    map.put("result", gson.toJsonTree(result.getResult()));
    return map;
  }

  /**
   * Builds the type mapping used by the chaining engine to interpret structured output fields.
   *
   * <p>An injector contract (or payload) declares its output fields with a {@link
   * ContractOutputType}, e.g. field "ports" -> PORT. The chaining engine does not work with
   * contract types directly. Instead, {@link ChainingTypeRegistry} translates each contract type
   * into a {@link ChainingMappedType} that tells the engine how to treat the produced values:
   *
   * <ul>
   *   <li>PRIMITIVE: scalar values stored per {@link PrimitiveType} (e.g. PORT ->
   *       PrimitiveType.Port)
   *   <li>COMPLEX: JSON objects stored as correlated pairs (e.g. PORTSCAN -> host+port tuples)
   *   <li>NOT_CHAINABLE: values ignored by the chaining engine entirely
   * </ul>
   *
   * <p>Two sources are supported:
   *
   * <ul>
   *   <li>Payload-based inject: output parsers define the fields and their types
   *   <li>Contract-based inject: the injector contract defines the fields and their types
   * </ul>
   *
   * @param inject the inject whose output fields are being mapped
   * @return map of output field name to its resolved chaining type
   */
  private Map<String, ChainingMappedType> buildTypeMappingsFromInject(Inject inject) {
    Map<String, ChainingMappedType> typeMappings = new HashMap<>();

    if (inject.getPayload().isPresent()) {
      Set<OutputParser> outputParsers = structuredOutputUtils.extractOutputParsers(inject);
      injectorContractContentUtils
          .getAllContractOutputs(outputParsers)
          .forEach(
              out ->
                  typeMappings.put(
                      out.getKey(),
                      ChainingTypeRegistry.getMappedTypeForContractOutputType(out.getType())));
    } else if (inject.getInjectorContract().isPresent()) {
      injectorContractContentUtils
          .getAllContractOutputs(inject.getInjectorContract().get())
          .forEach(
              out ->
                  typeMappings.put(
                      out.getField(),
                      ChainingTypeRegistry.getMappedTypeForContractOutputType(out.getType())));
    }
    return typeMappings;
  }
}
