package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetService;
import io.openaev.service.TeamService;
import io.openaev.service.UserService;
import io.openaev.service.chaining.ConditionService;
import io.openaev.service.chaining.StepService;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.helpers.InjectTestHelper;
import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class InjectExecutionStepTest {
  @MockitoBean private InjectorContractService injectorContractService;
  @MockitoBean private UserService userService;
  @MockitoBean private TeamService teamService;
  @MockitoBean private AssetService assetService;
  @MockitoBean private TagService tagService;
  @MockitoBean private DocumentService documentService;
  @MockitoBean private InjectService injectService;
  @MockitoBean private io.openaev.executors.Executor executor;
  @MockitoBean private InjectStatusService injectStatusService;
  @MockitoBean private ConditionService conditionService;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired InjectExecutionStep injectExecutionStep;
  ObjectMapper mapper = new ObjectMapper();
  @Autowired private InjectTestHelper injectTestHelper;
  String injectInputJson;
  InjectorContract injectorContractSaved;

  @BeforeEach
  void beforeEach() throws Exception {
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Injector injectorSaved = injectorRepository.save(injector);

    InjectorContract injectorContract = InjectorContractFixture.createImplantInjectorContract();
    injectorContract.setInjector(injectorSaved);
    injectorContractSaved = injectorContractRepository.save(injectorContract);

    doReturn(injectorContractSaved).when(injectorContractService).injectorContract(any());
    doReturn(new User()).when(userService).currentUser();
    doReturn(new ArrayList<>()).when(teamService).getTeamsByIds(any());
    doReturn(new ArrayList<>()).when(assetService).assets(any());
    doReturn(new HashSet<>()).when(tagService).tagSet(any());
    doReturn(null).when(documentService).document(any());
    doReturn(false).when(injectService).canApplyTargetType(any(), any());
    doReturn(new InjectStatus()).when(executor).directExecute(any());
    doReturn(Collections.emptyList()).when(conditionService).findAllConditionsByStepId(any());
    doReturn(true).when(conditionService).isMapperCondition(any());

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

    ExecutionTrace executionTrace = new ExecutionTrace();
    executionTrace.setStatus(ExecutionTraceStatus.SUCCESS);

    Agent agent = AgentFixture.createDefaultAgentService();

    executionTrace.setAgent(agent);
    executionTrace.setMessage("{\"test\": \"testValue\"}");

    InjectStatus injectStatus = new InjectStatus();
    injectStatus.addTrace(executionTrace);

    injectExecuted.setStatus(injectStatus);
    doReturn(injectExecuted).when(injectService).findInjectOrNull(any());
    Asset asset = AssetFixture.createDefaultAsset("AssetTest");
    asset = injectTestHelper.forceSaveAsset(asset);

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
                injectorContractSaved.getInjector().getId(),
                asset.getId());
  }

  @Test
  void given_mapperInput_should_updateContractPayloadArguments() {
    // Arrange
    Step step = new Step();
    step.setId("step-1");
    step.setInput("{\"IPv4\":\"10.10.10.10\"}");

    String contentJson = "{\"target_ip\":\"0.0.0.0\",\"file\":\"script.bat\"}";

    Condition mapperCondition = new Condition();
    mapperCondition.setType(ConditionType.MAPPER);
    mapperCondition.setKeyType(ConditionKeyType.IPv4);
    mapperCondition.setKey("target_ip");

    doReturn(List.of(mapperCondition)).when(conditionService).findAllConditionsByStepId("step-1");

    // Act
    com.fasterxml.jackson.databind.node.ObjectNode updated =
        ReflectionTestUtils.invokeMethod(
            injectExecutionStep, "updateContentWithInputs", step, contentJson);

    // Assert
    assertNotNull(updated);
    assertEquals("10.10.10.10", updated.get("target_ip").asText());
    assertEquals("script.bat", updated.get("file").asText());
  }

  @Test
  void given_emptyStepInput_should_keepOriginalContractContent() {
    // Arrange
    Step step = new Step();
    step.setId("step-2");
    step.setInput("{}");
    String contentJson = "{\"target_ip\":\"0.0.0.0\"}";

    // Act
    com.fasterxml.jackson.databind.node.ObjectNode updated =
        ReflectionTestUtils.invokeMethod(
            injectExecutionStep, "updateContentWithInputs", step, contentJson);

    // Assert
    assertNotNull(updated);
    assertEquals("0.0.0.0", updated.get("target_ip").asText());
    verify(conditionService, never()).findAllConditionsByStepId("step-2");
  }

  @Test
  void given_invalidContractContent_should_returnEmptyObject() {
    // Arrange
    Step step = new Step();
    step.setId("step-3");
    step.setInput("{\"IPv4\":\"10.10.10.10\"}");

    // Act
    com.fasterxml.jackson.databind.node.ObjectNode updated =
        ReflectionTestUtils.invokeMethod(
            injectExecutionStep, "updateContentWithInputs", step, "{invalid-json");

    // Assert
    assertNotNull(updated);
    assertTrue(updated.isEmpty());
  }

  @Test
  void create_shouldThrowException_whenStepDataIsNull() {
    StepInput stepInput = new StepInput();
    Workflow workflow = new Workflow();

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
   *   <li>An Inject step is generated using {@link InjectExecutionStep#toStepInput(InjectInput)}
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
    StepInput step = InjectExecutionStep.toStepInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyType(ConditionKeyType.IPv4)
            .value("output.message.ip")
            .stepFrom("firstStep")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));

    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();

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
    assertEquals(
        "output.message.ip", StepService.getField(stepTemplate.getInput(), "input.0.value"));
    assertEquals(
        "firstStep", StepService.getField(stepTemplate.getInput(), "input.0.id_step_from"));
    assertEquals(
        ConditionKeyType.IPv4.name(),
        StepService.getField(stepTemplate.getInput(), "input.0.keyType"));
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

    StepInput step = InjectExecutionStep.toStepInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyType(ConditionKeyType.IPv4)
            .value("output.message.ip")
            .stepFrom("firstStep")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));

    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();

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

    mapper.readValue(injectInputJson, InjectInput.class);
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepInput step = InjectExecutionStep.toStepInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyType(ConditionKeyType.IPv4)
            .value("output.message.ip")
            .stepFrom("firstStep")
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
  }

  @Test
  public void run_shouldReturnNull_whenInjectorIsNotFoundInDatabase()
      throws JsonProcessingException, ChainingException {
    // PREPARE

    // New StepsCreateInput & ConditionCreateInput
    mapper.readValue(injectInputJson, InjectInput.class);
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepInput step = InjectExecutionStep.toStepInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyType(ConditionKeyType.IPv4)
            .value("output.message.ip")
            .stepFrom("firstStep")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));
    // ACT CREATE + READY + RUN

    // PERSIST STEP TEMPLATE
    Optional<Step> stepTemplateOpt =
        injectExecutionStep.create(step, WorkflowFixture.getDefaultWorkflowTemplate());
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    // SIMUL LAUNCH WORKFLOW
    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);

    Optional<Step> stepReadyOpt =
        injectExecutionStep.ready(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
    assertTrue(stepReadyOpt.isPresent());
    Step stepReady = stepReadyOpt.get();

    String injectorId =
        StepService.getField(
            stepReady.getData(), "inject_injector_contract.injector_contract_injector");
    assertNotNull(injectorId);
    injectorRepository.deleteById(injectorId);

    // ACT
    ChainingException ex =
        Assertions.assertThrows(ChainingException.class, () -> injectExecutionStep.run(stepReady));
    // ASSERT
    Assertions.assertEquals("Step (READY) : Error processing JSON to Inject ", ex.getMessage());
  }

  @Test
  public void run_shouldReturnNull_whenInjectorIsNotFoundInDatabase2()
      throws JsonProcessingException, ChainingException {
    // PREPARE
    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();

    mapper.readValue(injectInputJson, InjectInput.class);
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepInput step = InjectExecutionStep.toStepInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyType(ConditionKeyType.IPv4)
            .value("output.message.ip")
            .stepFrom("firstStep")
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

    String injectorId =
        StepService.getField(
            stepReady.getData(), "inject_injector_contract.injector_contract_injector");
    assertNotNull(injectorId);
    stepReady.setData(
        StepService.setField(
            stepReady.getData(), "inject_injector_contract.injector_contract_injector", ""));

    Optional<Step> stepRunOpt = injectExecutionStep.run(stepReady);

    // ASSERT
    assertTrue(stepRunOpt.isPresent());
    assertNotNull(StepService.getField(stepRunOpt.get().getData(), "inject_id"));
  }

  @Test
  public void shouldFailInjectStatusAndReturnNull_whenExecutorThrowsException() throws Exception {
    // PREPARE
    RuntimeException exception = new RuntimeException("direct execute throw an exception");

    doThrow(exception).when(executor).directExecute(any());

    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();

    mapper.readValue(injectInputJson, InjectInput.class);
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepInput step = InjectExecutionStep.toStepInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyType(ConditionKeyType.IPv4)
            .value("output.message.ip")
            .stepFrom("firstStep")
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

    verify(injectStatusService).failInjectStatus(any(), eq("direct execute throw an exception"));

    // ASSERT
    Assertions.assertTrue(ex.getMessage().contains("Inject execution failed. Inject ID: "));
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
    mapper.readValue(injectInputJson, InjectInput.class);
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepInput step = InjectExecutionStep.toStepInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyType(ConditionKeyType.IPv4)
            .value("output.message.ip")
            .stepFrom("firstStep")
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
}
