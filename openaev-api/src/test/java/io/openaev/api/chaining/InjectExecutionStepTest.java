package io.openaev.api.chaining;

import static io.openaev.database.model.InjectorContract.AVAILABLE_EXPECTATIONS;
import static io.openaev.database.model.InjectorContract.IS_PREDEFINED_EXPECTATION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.openaev.IntegrationTest;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.service.UserService;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService;
import io.openaev.service.chaining.ConditionService;
import io.openaev.service.chaining.ScopeService;
import io.openaev.service.chaining.StepService;
import io.openaev.utils.ConditionUtils;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.helpers.InjectTestHelper;
import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class InjectExecutionStepTest extends IntegrationTest {

  @MockitoBean private InjectorContractService injectorContractService;
  @MockitoBean private UserService userService;
  @MockitoBean private InjectService injectService;
  @MockitoBean private ConditionService conditionService;
  @MockitoBean private ConditionUtils conditionUtils;
  @MockitoBean private io.openaev.executors.Executor executor;
  @MockitoBean private ScopeService scopeService;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired InjectExecutionStep injectExecutionStep;
  @Autowired AttackPathExecutionIngestionService attackPathExecutionIngestionService;
  ObjectMapper mapper = new ObjectMapper();
  @Autowired private InjectTestHelper injectTestHelper;
  String injectInputJson;
  InjectorContract injectorContractSaved;
  Asset savedAsset;

  @BeforeEach
  void beforeEach() throws Exception {
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Injector injectorSaved = injectorRepository.save(injector);

    InjectorContract injectorContract = InjectorContractFixture.createImplantInjectorContract();
    injectorContract.addInjector(injectorSaved);
    injectorContractSaved = injectorContractRepository.save(injectorContract);
    injectorSaved.linkContract(injectorContractSaved);
    injectorRepository.save(injectorSaved);

    doReturn(injectorContractSaved).when(injectorContractService).injectorContract(any());
    doReturn(new User()).when(userService).currentUser();
    doReturn(false).when(injectService).canApplyTargetType(any(), any());
    doReturn(new InjectStatus()).when(executor).directExecute(any());

    doAnswer(
            invocation -> {
              Inject inject = invocation.getArgument(0);
              return injectRepository.save(inject);
            })
        .when(injectService)
        .createInject(any(Inject.class));

    // UPDATE STEP:
    Inject injectExecuted = new Inject();
    injectExecuted.setId("INJECT-ID");

    injectExecuted.setTenant(new Tenant(io.openaev.context.TenantContext.getCurrentTenant()));

    ExecutionTrace executionTrace = new ExecutionTrace();
    executionTrace.setStatus(ExecutionTraceStatus.EXECUTED);

    Agent agent = AgentFixture.createDefaultAgentService();

    executionTrace.setAgent(agent);
    executionTrace.setMessage("{\"test\": \"testValue\"}");

    InjectStatus injectStatus = new InjectStatus();
    injectStatus.addTrace(executionTrace);

    injectExecuted.setStatus(injectStatus);
    doReturn(injectExecuted).when(injectService).inject(any());
    doReturn(injectExecuted).when(injectService).findInjectOrNull(any());
    Asset asset = AssetFixture.createDefaultAsset("AssetTest");
    asset = injectTestHelper.forceSaveAsset(asset);
    savedAsset = asset;

    // Mock scope service: return the saved asset so that hasAssetTargets = true in run()
    doReturn(List.of(savedAsset)).when(scopeService).getValidAssets(any());
    doReturn(List.of()).when(scopeService).getValidManualTargetsFromScopeAndGlobalState(any());

    injectInputJson =
        """
            {
                                                "type": "inject",
                                                "inject_title": "whoami",
                                                "inject_description": "",
                                                "inject_injector_contract": "%s",
                                                "inject_injector": "%s",
                                                "inject_content": {
                                                  "expectations": [
                                                    {
                                                      "expectation_type": "PREVENTION",
                                                      "expectation_name": "Prevention",
                                                      "expectation_description": null,
                                                      "expectation_score": 100,
                                                      "expectation_expectation_group": false,
                                                      "expectation_expiration_time": 21600
                                                    },
                                                    {
                                                      "expectation_type": "DETECTION",
                                                      "expectation_name": "Detection",
                                                      "expectation_description": null,
                                                      "expectation_score": 100,
                                                      "expectation_expectation_group": false,
                                                      "expectation_expiration_time": 21600
                                                    }
                                                  ],
                                                  "obfuscator": "plain-text",
                                                    "file": "c:\\\\programdata\\\\microsoft\\\\drm\\\\182.bat"
                                            },
                                                "inject_depends_on": [],
                                                "inject_depends_duration": 100,
                                                "inject_teams": [],
                                                "inject_assets": [
                                                    "%s"
                                                ],
                                                "inject_asset_groups": [],
                                                "inject_documents": [],
                                                "inject_all_teams": false,
                                                "inject_country": null,
                                                "inject_city": null,
                                                "inject_tags": [],
                                                "inject_enabled": true
            }
            """
            .formatted(
                injectorContractSaved.getId(),
                injectorContractSaved.getInjectors().stream().findFirst().orElseThrow().getId(),
                asset.getId());
  }

  @Test
  void given_mapperInput_should_updateContractPayloadArguments() {
    // Arrange
    Step step = new Step();
    step.setId("step-1");
    step.setInput("{\"IPv4\":\"10.10.10.10\"}");

    ObjectNode contentNode = mapper.createObjectNode();
    contentNode.put("target_ip", "0.0.0.0");
    contentNode.put("file", "script.bat");

    Condition mapperCondition = new Condition();
    mapperCondition.setType(ConditionType.MAPPER);
    mapperCondition.setKeyTypes(List.of(PrimitiveType.IPv4));
    mapperCondition.setKey("target_ip");

    doReturn(List.of(mapperCondition)).when(conditionService).findAllConditionsByStepId("step-1");
    doReturn(true).when(conditionUtils).isMapperCondition(mapperCondition);

    // Act
    com.fasterxml.jackson.databind.node.ObjectNode updated =
        ReflectionTestUtils.invokeMethod(
            injectExecutionStep, "updateContentWithInputs", step, contentNode);

    // Assert
    assertNotNull(updated);
    assertEquals("10.10.10.10", updated.get("target_ip").asText());
    assertEquals("script.bat", updated.get("file").asText());
  }

  @Test
  void
      given_scopeWithSubnetAndExpandedValues_whenExpandTargetBatches_thenManualTargetsUseExpandedHosts() {
    // Arrange
    Workflow workflowRun = Workflow.builder().id("workflow-1").build();
    List<ConditionService.ExecutionBatch> batches =
        List.of(new ConditionService.ExecutionBatch("{}", List.of(), null));

    doReturn(List.of()).when(scopeService).getValidAssets("workflow-1");
    doReturn(List.of("192.168.10.0/26", "192.168.10.1", "192.168.10.2", "example.org"))
        .when(scopeService)
        .getValidManualTargetsFromScopeAndGlobalState("workflow-1");

    Step stepTemplate = new Step();

    // Act
    List<ConditionService.ExecutionBatch> expanded =
        injectExecutionStep.expandTargetBatches(batches, workflowRun, stepTemplate);

    // Assert
    assertEquals(3, expanded.size());
    List<String> resolvedManualTargets =
        expanded.stream()
            .map(ConditionService.ExecutionBatch::inputString)
            .map(JsonParser::parseString)
            .map(JsonElement::getAsJsonObject)
            .map(json -> json.getAsJsonObject("_target"))
            .map(target -> target.get("manual").getAsString())
            .toList();
    assertTrue(resolvedManualTargets.contains("192.168.10.1"));
    assertTrue(resolvedManualTargets.contains("192.168.10.2"));
    assertTrue(resolvedManualTargets.contains("example.org"));
    assertFalse(resolvedManualTargets.contains("192.168.10.0/26"));
  }

  @Test
  void given_emptyStepInput_should_keepOriginalContractContent() {
    // Arrange
    Step step = new Step();
    step.setId("step-2");
    step.setInput("{}");
    ObjectNode contentNode = mapper.createObjectNode();
    contentNode.put("target_ip", "0.0.0.0");

    // Act
    com.fasterxml.jackson.databind.node.ObjectNode updated =
        ReflectionTestUtils.invokeMethod(
            injectExecutionStep, "updateContentWithInputs", step, contentNode);

    // Assert
    assertNotNull(updated);
    assertEquals("0.0.0.0", updated.get("target_ip").asText());
    verify(conditionService, never()).findAllConditionsByStepId("step-2");
  }

  @Test
  void given_invalidStepInput_should_returnEmptyObject() {
    // Arrange
    Step step = new Step();
    step.setId("step-3");
    step.setInput("{invalid-json");
    ObjectNode contentNode = mapper.createObjectNode();
    contentNode.put("target_ip", "0.0.0.0");

    // Act
    com.fasterxml.jackson.databind.node.ObjectNode updated =
        ReflectionTestUtils.invokeMethod(
            injectExecutionStep, "updateContentWithInputs", step, contentNode);

    // Assert
    assertNotNull(updated);
    assertTrue(updated.isEmpty());
  }

  @Test
  void given_parsedContainsObjectArraysAndPrimitiveLists_should_extractValues() {
    // Arrange
    JsonObject parsed = new JsonObject();
    JsonArray portscan = new JsonArray();
    JsonObject portscanItem = new JsonObject();
    portscanItem.addProperty("asset_id", (String) null);
    portscanItem.addProperty("host", "0.0.0.0");
    portscanItem.addProperty("port", 135);
    portscanItem.addProperty("service", "TCP");
    JsonObject secondPortscanItem = new JsonObject();
    secondPortscanItem.addProperty("asset_id", (String) null);
    secondPortscanItem.addProperty("host", "127.0.0.1");
    secondPortscanItem.addProperty("port", 5432);
    secondPortscanItem.addProperty("service", "TCP");
    portscan.add(portscanItem);
    portscan.add(secondPortscanItem);
    parsed.add("portscan", portscan);

    JsonArray ips = new JsonArray();
    ips.add(new JsonPrimitive("0.0.0.0"));
    ips.add(new JsonPrimitive("127.0.0.1"));
    parsed.add("ips", ips);

    JsonArray ports = new JsonArray();
    ports.add(new JsonPrimitive(135));
    ports.add(new JsonPrimitive(5432));
    parsed.add("ports", ports);

    Map<String, JsonElement> outputEntry = new HashMap<>();
    outputEntry.put("parsed", parsed);

    // Act
    JsonObject extracted =
        ReflectionTestUtils.invokeMethod(
            injectExecutionStep, "extractDataFromParsed", List.of(outputEntry));

    // Assert
    assertNotNull(extracted);
    assertTrue(extracted.get("portscan").isJsonArray());
    assertEquals(2, extracted.getAsJsonArray("portscan").size());
    assertEquals(portscanItem, extracted.getAsJsonArray("portscan").get(0).getAsJsonObject());
    assertEquals(secondPortscanItem, extracted.getAsJsonArray("portscan").get(1).getAsJsonObject());
    assertEquals("0.0.0.0", extracted.getAsJsonArray("ips").get(0).getAsString());
    assertEquals("127.0.0.1", extracted.getAsJsonArray("ips").get(1).getAsString());
    assertEquals(135, extracted.getAsJsonArray("ports").get(0).getAsInt());
    assertEquals(5432, extracted.getAsJsonArray("ports").get(1).getAsInt());
  }

  @Test
  void given_parsedContainsSingleObject_should_wrapValueIntoArray() {
    // Arrange
    JsonObject parsed = new JsonObject();
    JsonObject credential = new JsonObject();
    credential.addProperty("username", "admin");
    credential.addProperty("password", "secret");
    parsed.add("credentials", credential);

    Map<String, JsonElement> outputEntry = new HashMap<>();
    outputEntry.put("parsed", parsed);

    // Act
    JsonObject extracted =
        ReflectionTestUtils.invokeMethod(
            injectExecutionStep, "extractDataFromParsed", List.of(outputEntry));

    // Assert
    assertNotNull(extracted);
    assertTrue(extracted.get("credentials").isJsonArray());
    assertEquals(1, extracted.getAsJsonArray("credentials").size());
    assertEquals(credential, extracted.getAsJsonArray("credentials").get(0).getAsJsonObject());
  }

  @Test
  void given_payloadOutputMarkedAsNonFinding_should_stillBuildTypeMapping() {
    // Arrange
    ContractOutputElement outputElement = new ContractOutputElement();
    outputElement.setKey("output");
    outputElement.setType(ContractOutputType.ActionOutput);
    outputElement.setName("output");
    outputElement.setRule(".*");
    outputElement.setFinding(false);
    OutputParser outputParser = new OutputParser();
    outputParser.setMode(ParserMode.STDOUT);
    outputParser.setType(ParserType.REGEX);
    outputParser.addContractOutputElement(outputElement);

    Payload payload = new Payload();
    payload.addOutputParser(outputParser);

    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setPayload(payload);

    Inject inject = new Inject();
    inject.setInjectorContract(injectorContract);

    // Act
    @SuppressWarnings("unchecked")
    Map<String, ChainingMappedType> typeMappings =
        ReflectionTestUtils.invokeMethod(
            injectExecutionStep, "buildTypeMappingsFromInject", inject);

    // Assert
    assertNotNull(typeMappings);
    assertTrue(typeMappings.containsKey("output"));
    ChainingMappedType mappedType = typeMappings.get("output");
    assertNotNull(mappedType);
    assertEquals(ChainingTypeKind.PRIMITIVE, mappedType.kind());
    assertEquals(List.of(PrimitiveType.ActionOutput), mappedType.primitiveTypes());
  }

  @Test
  void create_shouldThrowException_whenStepDataIsNull() {
    StepsCreateInput.StepInput stepInput = new StepsCreateInput.StepInput();
    Workflow workflow = new Workflow();
    workflow.setSimulation(ExerciseFixture.createDefaultExercise());

    IllegalArgumentException ex =
        Assertions.assertThrows(
            IllegalArgumentException.class, () -> injectExecutionStep.create(stepInput, workflow));

    Assertions.assertEquals("Data step of new step (TEMPLATE) is null", ex.getMessage());
  }

  @Test
  void run_shouldReturnNull_whenJsonIsInvalid() {
    Step step = new Step();
    step.setData("{ invalid json }");

    ChainingException ex =
        Assertions.assertThrows(ChainingException.class, () -> injectExecutionStep.run(step));
    Assertions.assertEquals("Step (READY) : Error processing JSON to Inject ", ex.getMessage());
  }

  @Test
  void run_shouldReturnNull_whenInjectHasNoInjectorContract() {
    // PREPARE
    Step step = new Step();
    step.setId("step-ID");
    step.setData("{}");
    // ACT
    ChainingException ex =
        Assertions.assertThrows(ChainingException.class, () -> injectExecutionStep.run(step));
    // ASSERT
    Assertions.assertEquals(
        "Injector contract not found for step (READY) ID: step-ID", ex.getMessage());
  }

  /**
   * Tests the creation of a step (InjectExecutionAction) from an InjectInput.
   *
   * <p>This test verifies that:
   *
   * <ul>
   *   <li>An {@link InjectInput} JSON payload is correctly deserialized
   *   <li>An Inject step is generated using {@link
   *       InjectExecutionStep#getInjectAsStepsCreateInput(InjectInput)}
   *   <li>A MAPPER condition is correctly transformed into step input mapping
   *   <li>The step template is created with the expected action and status
   *   <li>The step data contains a valid serialized inject with its injector contract
   *   <li>The step input correctly references the source step, path, and key
   * </ul>
   *
   * <p>This ensures that an Inject can be converted into a workflow step template with proper input
   * mapping and metadata.
   */
  @Test
  public void createTest() throws JsonProcessingException, ChainingException {
    // PREPARE
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyTypes(List.of(PrimitiveType.IPv4))
            .value("output.message.ip")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));

    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());

    // ACT
    Optional<Step> stepTemplateOpt = injectExecutionStep.create(step, workflowTemplate);
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    // ASSERT
    assertEquals(StepActionClass.INJECT_EXECUTION, stepTemplate.getStepAction());
    assertEquals(StepStatus.TEMPLATE, stepTemplate.getStatus());
    assertFalse(stepTemplate.getData().isEmpty());
    assertFalse(stepTemplate.getData().isBlank());
    assertEquals(
        injectorContractSaved.getId(),
        StepService.getField(
            stepTemplate.getData(), "inject_injector_contract.injector_contract_id"));
    assertEquals("output.message.ip", StepService.getField(stepTemplate.getInput(), "input.path"));
    assertEquals("[\"IPv4\"]", StepService.getField(stepTemplate.getInput(), "input.keyTypes"));
  }

  /**
   * Tests the transition of a step (InjectExecutionAction) from TEMPLATE to READY (ready state).
   *
   * <p>This test verifies that:
   *
   * <ul>
   *   <li>A step template (InjectExecutionAction) can be converted into a READY step
   *   <li>The input provided at runtime is correctly set on the READY step
   *   <li>The step is properly associated with a workflow in RUN state
   * </ul>
   *
   * <p>This ensures that a step (InjectExecutionAction) is correctly prepared for execution with
   * runtime-specific input.
   */
  @Test
  public void readyTest() throws JsonProcessingException, ChainingException {
    // PREPARE
    mapper.readValue(injectInputJson, InjectInput.class);
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);

    StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyTypes(List.of(PrimitiveType.IPv4))
            .value("output.message.ip")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));

    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());

    // ACT
    Optional<Step> stepTemplateOpt = injectExecutionStep.create(step, workflowTemplate);
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);

    Optional<Step> stepReadyOpt =
        injectExecutionStep.ready(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
    assertTrue(stepReadyOpt.isPresent());
    Step stepReady = stepReadyOpt.get();
    // ASSERT
    assertEquals("do defined", StepService.getField(stepReady.getInput(), "input"));
  }

  /**
   * Tests the execution of a step (InjectExecutionAction).
   *
   * <p>This test verifies that:
   *
   * <ul>
   *   <li>A READY step can be executed
   *   <li>The inject is created and executed during the RUN phase
   *   <li>The inject identifier is correctly injected back into the step data
   * </ul>
   *
   * <p>This ensures that the execution phase of an Inject Execution step properly updates the step
   * state with runtime execution information.
   */
  @Test
  public void runTest() throws JsonProcessingException, ChainingException {
    // PREPARE
    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());

    mapper.readValue(injectInputJson, InjectInput.class);
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyTypes(List.of(PrimitiveType.IPv4))
            .value("output.message.ip")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));
    // ACT

    Optional<Step> stepTemplateOpt = injectExecutionStep.create(step, workflowTemplate);
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);

    Optional<Step> stepReadyOpt =
        injectExecutionStep.ready(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
    assertTrue(stepReadyOpt.isPresent());
    Step stepReady1 = stepReadyOpt.get();
    Optional<Step> stepReadyOpt2 = injectExecutionStep.run(stepReady1);
    assertTrue(stepReadyOpt2.isPresent());
    Step stepReady = stepReadyOpt2.get();

    // ASSERT
    assertNotNull(StepService.getField(stepReady.getData(), "inject_id"));
    String injectId = StepService.getField(stepReady.getData(), "inject_id");
    Inject createdInject = injectRepository.findById(injectId).orElseThrow();
    assertEquals(2, createdInject.getContent().withArray("expectations").size());
  }

  @Test
  public void run_shouldSetPredefinedExpectations_whenStepHasNoExpectationsInData()
      throws JsonProcessingException, ChainingException {
    // PREPARE
    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());
    JsonNode injectorContractContent = mapper.readTree(injectorContractSaved.getContent());
    int expectedPredefinedExpectations = 0;
    for (JsonNode field : injectorContractContent.path("fields")) {
      if ("expectations".equals(field.path("key").asText())) {
        JsonNode available = field.path(AVAILABLE_EXPECTATIONS);
        for (JsonNode exp : available) {
          if (exp.path(IS_PREDEFINED_EXPECTATION).asBoolean(false)) {
            expectedPredefinedExpectations++;
          }
        }
        break;
      }
    }

    ObjectNode injectInputNode = (ObjectNode) mapper.readTree(injectInputJson);
    ((ObjectNode) injectInputNode.get("inject_content")).remove("expectations");
    InjectInput injectInput = mapper.treeToValue(injectInputNode, InjectInput.class);
    StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyTypes(List.of(PrimitiveType.IPv4))
            .value("output.message.ip")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));

    // ACT
    Optional<Step> stepTemplateOpt = injectExecutionStep.create(step, workflowTemplate);
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);

    Optional<Step> stepReadyOpt =
        injectExecutionStep.ready(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
    assertTrue(stepReadyOpt.isPresent());
    Step stepReady = stepReadyOpt.get();

    Optional<Step> stepRunOpt = injectExecutionStep.run(stepReady);
    assertTrue(stepRunOpt.isPresent());

    // ASSERT
    String injectId = StepService.getField(stepReady.getData(), "inject_id");
    Inject createdInject = injectRepository.findById(injectId).orElseThrow();
    assertEquals(
        expectedPredefinedExpectations,
        createdInject.getContent().withArray("expectations").size());
  }

  @Test
  public void run_shouldKeepStepExpectations_whenCustomExpectationProvidedInStepData()
      throws JsonProcessingException, ChainingException {
    // PREPARE
    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());

    ObjectNode injectInputNode = (ObjectNode) mapper.readTree(injectInputJson);
    ObjectNode injectContentNode = (ObjectNode) injectInputNode.get("inject_content");
    ObjectNode customExpectationNode = mapper.createObjectNode();
    customExpectationNode.put("expectation_type", "DETECTION");
    customExpectationNode.put("expectation_name", "Custom detection expectation");
    customExpectationNode.putNull("expectation_description");
    customExpectationNode.put("expectation_score", 80);
    customExpectationNode.put("expectation_expectation_group", false);
    customExpectationNode.put("expectation_expiration_time", 3600);
    injectContentNode.set("expectations", mapper.createArrayNode().add(customExpectationNode));

    InjectInput injectInput = mapper.treeToValue(injectInputNode, InjectInput.class);
    StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyTypes(List.of(PrimitiveType.IPv4))
            .value("output.message.ip")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));

    // ACT
    Optional<Step> stepTemplateOpt = injectExecutionStep.create(step, workflowTemplate);
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);

    Optional<Step> stepReadyOpt =
        injectExecutionStep.ready(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
    assertTrue(stepReadyOpt.isPresent());
    Step stepReady = stepReadyOpt.get();

    Optional<Step> stepRunOpt = injectExecutionStep.run(stepReady);
    assertTrue(stepRunOpt.isPresent());

    // ASSERT
    String injectId = StepService.getField(stepReady.getData(), "inject_id");
    Inject createdInject = injectRepository.findById(injectId).orElseThrow();
    assertEquals(1, createdInject.getContent().withArray("expectations").size());
    assertEquals(
        "Custom detection expectation",
        createdInject
            .getContent()
            .withArray("expectations")
            .get(0)
            .path("expectation_name")
            .asText());
  }

  @Test
  public void run_shouldReturnNull_whenInjectorIsNotFoundInDatabase()
      throws JsonProcessingException, ChainingException {
    // PREPARE

    // New StepsCreateInput & ConditionCreateInput
    mapper.readValue(injectInputJson, InjectInput.class);
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyTypes(List.of(PrimitiveType.IPv4))
            .value("output.message.ip")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));
    // ACT CREATE + READY + RUN

    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());
    // PERSIST STEP TEMPLATE
    Optional<Step> stepTemplateOpt = injectExecutionStep.create(step, workflowTemplate);
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    // SIMUL LAUNCH WORKFLOW
    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);

    Optional<Step> stepReadyOpt =
        injectExecutionStep.ready(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
    assertTrue(stepReadyOpt.isPresent());
    Step stepReady = stepReadyOpt.get();

    String injectorIdsJson =
        StepService.getField(
            stepReady.getData(), "inject_injector_contract.injector_contract_injectors");
    assertNotNull(injectorIdsJson);
    String[] injectorIds = mapper.readValue(injectorIdsJson, String[].class);
    for (String id : injectorIds) {
      injectorRepository.deleteByIdAndTenantId(
          id, io.openaev.context.TenantContext.getCurrentTenant());
    }
    entityManager.flush();
    entityManager.clear();

    // Clear injectors from the mocked contract so the code cannot resolve them
    injectorContractSaved.clearInjectors();

    // ACT
    ChainingException ex =
        Assertions.assertThrows(ChainingException.class, () -> injectExecutionStep.run(stepReady));
    // ASSERT
    Assertions.assertEquals(
        "Injector not found for injectorId "
            + injectorIds[0]
            + " and step (READY) ID "
            + stepReady.getId(),
        ex.getMessage());
  }

  @Test
  public void shouldFailInjectStatusAndReturnNull_whenExecutorThrowsException() throws Exception {
    // PREPARE
    RuntimeException exception = new RuntimeException("direct execute throw an exception");

    doThrow(exception).when(executor).directExecute(any());

    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());

    mapper.readValue(injectInputJson, InjectInput.class);
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyTypes(List.of(PrimitiveType.IPv4))
            .value("output.message.ip")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));

    // ACT
    Optional<Step> stepTemplateOpt = injectExecutionStep.create(step, workflowTemplate);
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    workflowRun.setSimulation(workflowTemplate.getSimulation());

    Optional<Step> stepReadyOpt =
        injectExecutionStep.ready(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
    assertTrue(stepReadyOpt.isPresent());
    Step stepReady = stepReadyOpt.get();

    ChainingException ex =
        Assertions.assertThrows(ChainingException.class, () -> injectExecutionStep.run(stepReady));

    // ASSERT

    verify(executor).directExecute(any());

    // ASSERT
    Assertions.assertTrue(ex.getMessage().contains("Inject execution failed. Inject ID: "));
    String idInject = ex.getMessage().replace("Inject execution failed. Inject ID: ", "");
    Assertions.assertFalse(
        injectRepository.findById(idInject).isPresent(), idInject + " should not be persisted");
  }

  /**
   * Tests the update phase of an Inject Execution step.
   *
   * <p>This test verifies that:
   *
   * <ul>
   *   <li>A RUN step (InjectExecutionAction) can be updated using its inject execution status
   *   <li>Execution traces are correctly transformed into step output
   *   <li>The step output contains agent information and execution messages
   * </ul>
   *
   * <p>This ensures that execution results are properly exposed through the step output after an
   * inject run.
   */
  @Test
  public void updateTest() throws JsonProcessingException, ChainingException {
    // PREPARE
    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());

    mapper.readValue(injectInputJson, InjectInput.class);
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyTypes(List.of(PrimitiveType.IPv4))
            .value("output.message.ip")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));
    // ACT
    Optional<Step> stepTemplateOpt = injectExecutionStep.create(step, workflowTemplate);
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    Optional<Step> stepReadyOpt =
        injectExecutionStep.ready(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
    assertTrue(stepReadyOpt.isPresent());
    Step stepReady = stepReadyOpt.get();

    Optional<Step> stepReadyOpt2 = injectExecutionStep.run(stepReady);
    assertTrue(stepReadyOpt2.isPresent());
    Step stepRun = stepReadyOpt2.get();

    stepRun.setStatus(StepStatus.RUN);
    Optional<Step> runUpdatedOpt = injectExecutionStep.update(stepRun);
    assertTrue(runUpdatedOpt.isPresent());
    Step runUpdated = runUpdatedOpt.get();

    // ASSERT
    assertNotNull(StepService.getField(runUpdated.getOutput(), "outputs.agent_id"));
    assertEquals("testValue", StepService.getField(runUpdated.getOutput(), "outputs.message.test"));
  }

  public static InjectorContract getInjectorContract() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setContent(
        "{\"config\":{\"type\":\"openaev_implant\",\"expose\":true,\"label\":{\"en\":\"OpenAEV Implant\",\"fr\":\"OpenAEV Implant\"},\"color_dark\":\"#000000\",\"color_light\":\"#000000\"},\"label\":{\"en\":\"WHOAMI\",\"fr\":\"WHOAMI\"},\"manual\":false,\"fields\":[{\"key\":\"assets\",\"label\":\"Source assets\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":[\"assets\",\"asset_groups\"],\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"n\",\"defaultValue\":[],\"type\":\"asset\"},{\"key\":\"asset_groups\",\"label\":\"Source asset groups\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":[\"assets\",\"asset_groups\"],\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"n\",\"defaultValue\":[],\"type\":\"asset-group\"},{\"key\":\"obfuscator\",\"label\":\"Obfuscators\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":null,\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"1\",\"defaultValue\":[\"plain-text\"],\"choices\":[{\"label\":\"plain-text\",\"value\":\"plain-text\",\"information\":\"\"},{\"label\":\"base64\",\"value\":\"base64\",\"information\":\"CMD does not support base64 obfuscation\"}],\"type\":\"choice\"},{\"key\":\"expectations\",\"label\":\"Expectations\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":null,\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"n\",\"defaultValue\":[],\"availableExpectations\":[{\"expectation_type\":\"PREVENTION\",\"expectation_name\":\"Prevention\",\"expectation_description\":null,\"expectation_score\":100.0,\"expectation_expectation_group\":false,\"expectation_expiration_time\":21600,\"expectation_is_predefined\":true},{\"expectation_type\":\"DETECTION\",\"expectation_name\":\"Detection\",\"expectation_description\":null,\"expectation_score\":100.0,\"expectation_expectation_group\":false,\"expectation_expiration_time\":21600,\"expectation_is_predefined\":true}],\"type\":\"expectation\"}],\"variables\":[{\"key\":\"user\",\"label\":\"User that will receive the injection\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[{\"key\":\"user.id\",\"label\":\"Id of the user in the platform\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.email\",\"label\":\"Email of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.firstname\",\"label\":\"First name of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.lastname\",\"label\":\"Last name of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.lang\",\"label\":\"Language of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]}]},{\"key\":\"exercise\",\"label\":\"Exercise of the current injection\",\"type\":\"Object\",\"cardinality\":\"1\",\"children\":[{\"key\":\"exercise.id\",\"label\":\"Id of the user in the platform\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"exercise.name\",\"label\":\"Name of the exercise\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"exercise.description\",\"label\":\"Description of the exercise\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]}]},{\"key\":\"teams\",\"label\":\"List of team name for the injection\",\"type\":\"String\",\"cardinality\":\"n\",\"children\":[]},{\"key\":\"player_uri\",\"label\":\"Player interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"challenges_uri\",\"label\":\"Challenges interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"scoreboard_uri\",\"label\":\"Scoreboard interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"lessons_uri\",\"label\":\"Lessons learned interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]}],\"context\":{},\"contract_id\":\"73bfd988-b0bd-4740-bb7e-a6209a538835\",\"contract_attack_patterns_external_ids\":[],\"is_atomic_testing\":true,\"needs_executor\":true,\"platforms\":[\"MacOS\"],\"domains\":[{\"listened\":true,\"domain_id\":\"948e3cdc-c345-45dd-80cb-943804c09a3a\",\"domain_name\":\"Endpoint\",\"domain_color\":\"#389CFF\",\"domain_created_at\":\"2026-02-03T12:15:01.323228Z\",\"domain_updated_at\":\"2026-02-03T12:15:01.323228Z\"}]}");
    injectorContract.setConvertedContent(
        (ObjectNode) mapper.readTree(injectorContract.getContent()));
    injectorContract.setId("73bfd988-b0bd-4740-bb7e-a6209a538835");
    Map<String, String> labels = new HashMap<>();
    labels.put("en", "WHOAMI");
    labels.put("fr", "WHOAMI");
    injectorContract.setLabels(labels);
    injectorContract.setManual(false);
    Injector injector = new Injector();
    injector.setId("injectorId");
    injectorContract.addInjector(injector);
    injectorContract.setAtomicTesting(false);
    injectorContract.setCustom(false);
    injectorContract.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.MacOS});
    injectorContract.setNeedsExecutor(true);
    injectorContract.setImportAvailable(false);

    return injectorContract;
  }

  @Test
  public void given_mapperCondition_should_includeKeyTypeInStepInput()
      throws JsonProcessingException, ChainingException {
    // Arrange
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyTypes(List.of(PrimitiveType.Username))
            .key("expectations")
            .value("output.message.credentials")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));

    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());

    // Act
    Optional<Step> stepTemplateOpt = injectExecutionStep.create(step, workflowTemplate);
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    // Assert
    assertEquals("[\"Username\"]", StepService.getField(stepTemplate.getInput(), "input.keyTypes"));
  }

  @Test
  public void given_mapperConditionWithoutSubtype_should_not_includeKeySubtypeInStepInput()
      throws JsonProcessingException, ChainingException {
    // Arrange
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyTypes(List.of(PrimitiveType.IPv4))
            .key("target_ip")
            .value("output.message.ip")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));

    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());

    // Act
    Optional<Step> stepTemplateOpt = injectExecutionStep.create(step, workflowTemplate);
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    // Assert
    assertEquals("[\"IPv4\"]", StepService.getField(stepTemplate.getInput(), "input.keyTypes"));
    assertNull(StepService.getField(stepTemplate.getInput(), "input.keySubtype"));
  }

  @Test
  void given_injectWithoutStatus_whenGetCommand_thenReturnEmptyString() {
    // Arrange
    Inject inject = new Inject();

    // Act
    String command = ReflectionTestUtils.invokeMethod(injectExecutionStep, "getCommand", inject);

    // Assert
    assertEquals("", command);
  }

  @Test
  void given_injectWithPayloadCommands_whenGetCommand_thenResolvePayloadVariables() {
    // Arrange
    Inject inject = new Inject();
    InjectorContract contract = new InjectorContract();
    Command payload = new Command();
    payload.setExecutor("bash");
    payload.setContent("echo #{var} && whoami #{user}");

    PayloadArgument varArg = new PayloadArgument();
    varArg.setType(PrimitiveType.Text);
    varArg.setKey("var");
    varArg.setDefaultValue("eva");

    PayloadArgument userArg = new PayloadArgument();
    userArg.setType(PrimitiveType.Username);
    userArg.setKey("user");
    userArg.setDefaultValue("admin");

    payload.setArguments(List.of(varArg, userArg));
    contract.setPayload(payload);
    inject.setInjectorContract(contract);

    // Act
    String command = ReflectionTestUtils.invokeMethod(injectExecutionStep, "getCommand", inject);

    // Assert
    assertEquals("echo eva && whoami admin", command);
  }

  @Test
  void given_injectWithoutStatus_whenGetExecutionTracesByEndpointIndex_thenReturnEmptyMap() {
    // Arrange
    Inject inject = new Inject();

    // Act
    Map<String, StringBuilder> tracesByEndpointSource =
        ReflectionTestUtils.invokeMethod(
            attackPathExecutionIngestionService, "getExecutionTracesByEndpointIndex", inject);

    // Assert
    assertNotNull(tracesByEndpointSource);
    assertTrue(tracesByEndpointSource.isEmpty());
  }

  @Test
  void given_injectWithInjector_whenGetExecutionTracesByEndpointIndex_thenGroupByInjectorId() {
    // Arrange
    Inject inject = new Inject();
    inject.setId("inject-1");
    Injector injector = new Injector();
    injector.setId("injector-1");
    inject.setInjector(injector);
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setNeedsExecutor(false);
    inject.setInjectorContract(injectorContract);
    Asset targetAsset = new Asset();
    targetAsset.setId("asset-1");
    inject.setAssets(List.of(targetAsset));
    inject.setContent(mapper.createObjectNode().put("target_selector", "assets"));

    ExecutionTrace firstTrace = new ExecutionTrace();
    firstTrace.setStatus(ExecutionTraceStatus.EXECUTED);
    firstTrace.setMessage("first");

    ExecutionTrace secondTrace = new ExecutionTrace();
    secondTrace.setStatus(ExecutionTraceStatus.ERROR);
    secondTrace.setMessage("second");

    InjectStatus status = new InjectStatus();
    status.addTrace(firstTrace);
    status.addTrace(secondTrace);
    inject.setStatus(status);

    // Act
    Map<String, StringBuilder> tracesByEndpointSource =
        ReflectionTestUtils.invokeMethod(
            attackPathExecutionIngestionService, "getExecutionTracesByEndpointIndex", inject);

    // Assert
    assertNotNull(tracesByEndpointSource);
    assertEquals(1, tracesByEndpointSource.size());
    String expectedIndex = AttackPathIds.executionNode("inject-1", "asset-1", "injector-1");
    assertTrue(tracesByEndpointSource.containsKey(expectedIndex));
    assertEquals(
        "EXECUTED first\nERROR second\n", tracesByEndpointSource.get(expectedIndex).toString());
  }

  @Test
  void
      given_injectWithoutInjector_whenGetExecutionTracesByEndpointIndex_thenGroupByAgentAndAsset() {
    // Arrange
    Inject inject = new Inject();
    inject.setId("inject-2");
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setNeedsExecutor(true);
    DnsResolution dnsResolution = new DnsResolution();
    dnsResolution.setHostname("target.local");
    injectorContract.setPayload(dnsResolution);
    inject.setInjectorContract(injectorContract);

    Endpoint assetOne = new Endpoint();
    assetOne.setId("asset-1");
    Agent agentOne = new Agent();
    agentOne.setId("agent-1");
    agentOne.setAsset(assetOne);

    Endpoint assetTwo = new Endpoint();
    assetTwo.setId("asset-2");
    Agent agentTwo = new Agent();
    agentTwo.setId("agent-2");
    agentTwo.setAsset(assetTwo);

    ExecutionTrace firstTrace = new ExecutionTrace();
    firstTrace.setAgent(agentOne);
    firstTrace.setAction(ExecutionTraceAction.EXECUTION);
    firstTrace.setStatus(ExecutionTraceStatus.EXECUTED);
    firstTrace.setMessage("first");

    ExecutionTrace secondTrace = new ExecutionTrace();
    secondTrace.setAgent(agentOne);
    secondTrace.setAction(ExecutionTraceAction.EXECUTION);
    secondTrace.setStatus(ExecutionTraceStatus.ERROR);
    secondTrace.setMessage("second");

    ExecutionTrace thirdTrace = new ExecutionTrace();
    thirdTrace.setAgent(agentTwo);
    thirdTrace.setAction(ExecutionTraceAction.EXECUTION);
    thirdTrace.setStatus(ExecutionTraceStatus.INFO);
    thirdTrace.setMessage("third");

    InjectStatus status = new InjectStatus();
    status.addTrace(firstTrace);
    status.addTrace(secondTrace);
    status.addTrace(thirdTrace);
    inject.setStatus(status);
    doReturn(new AgentsAndAssetsAgentless(Set.of(agentOne, agentTwo), Set.of()))
        .when(injectService)
        .getAgentsAndAgentlessAssetsByInject(inject);

    // Act
    Map<String, StringBuilder> tracesByEndpointSource =
        ReflectionTestUtils.invokeMethod(
            attackPathExecutionIngestionService, "getExecutionTracesByEndpointIndex", inject);

    // Assert
    assertNotNull(tracesByEndpointSource);
    assertEquals(2, tracesByEndpointSource.size());
    String expectedIndexAgentOne =
        AttackPathIds.executionNode("inject-2", "target.local", "agent-1");
    String expectedIndexAgentTwo =
        AttackPathIds.executionNode("inject-2", "target.local", "agent-2");
    assertEquals("first\nsecond\n", tracesByEndpointSource.get(expectedIndexAgentOne).toString());
    assertEquals("third\n", tracesByEndpointSource.get(expectedIndexAgentTwo).toString());
  }

  @Test
  void given_injectWithoutInjector_whenTraceHasNoAgent_thenSkipAgentlessTrace() {
    // Arrange
    Inject inject = new Inject();
    inject.setId("inject-3");
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setNeedsExecutor(true);
    DnsResolution dnsResolution = new DnsResolution();
    dnsResolution.setHostname("target.local");
    injectorContract.setPayload(dnsResolution);
    inject.setInjectorContract(injectorContract);

    Endpoint asset = new Endpoint();
    asset.setId("asset-1");
    Agent agent = new Agent();
    agent.setId("agent-1");
    agent.setAsset(asset);

    ExecutionTrace globalTrace = new ExecutionTrace();
    globalTrace.setAgent(null);
    globalTrace.setAction(ExecutionTraceAction.EXECUTION);
    globalTrace.setStatus(ExecutionTraceStatus.WARNING);
    globalTrace.setMessage("global warning");

    ExecutionTrace endpointTrace = new ExecutionTrace();
    endpointTrace.setAgent(agent);
    endpointTrace.setAction(ExecutionTraceAction.EXECUTION);
    endpointTrace.setStatus(ExecutionTraceStatus.EXECUTED);
    endpointTrace.setMessage("endpoint ok");

    InjectStatus status = new InjectStatus();
    status.addTrace(globalTrace);
    status.addTrace(endpointTrace);
    inject.setStatus(status);
    doReturn(new AgentsAndAssetsAgentless(Set.of(agent), Set.of()))
        .when(injectService)
        .getAgentsAndAgentlessAssetsByInject(inject);

    // Act
    Map<String, StringBuilder> tracesByEndpointSource =
        ReflectionTestUtils.invokeMethod(
            attackPathExecutionIngestionService, "getExecutionTracesByEndpointIndex", inject);

    // Assert
    assertNotNull(tracesByEndpointSource);
    assertEquals(1, tracesByEndpointSource.size());
    String expectedIndex = AttackPathIds.executionNode("inject-3", "target.local", "agent-1");
    assertTrue(tracesByEndpointSource.containsKey(expectedIndex));
    assertEquals("endpoint ok\n", tracesByEndpointSource.get(expectedIndex).toString());
  }
}
