package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
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
import io.openaev.service.chaining.StepService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.helpers.InjectTestHelper;
import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class InjectExecutionStepTest extends IntegrationTest {
  @MockBean private InjectorContractService injectorContractService;
  @MockBean private UserService userService;
  @MockBean private TeamService teamService;
  @MockBean private AssetService assetService;
  @MockBean private TagService tagService;
  @MockBean private DocumentService documentService;
  @MockBean private InjectService injectService;
  @MockBean private io.openaev.executors.Executor executor;
  @MockBean private InjectStatusService injectStatusService;
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
    injectorContract.addInjector(injectorSaved);
    injectorContractSaved = injectorContractRepository.save(injectorContract);
    injectorSaved.getContracts().add(injectorContractSaved);
    injectorRepository.save(injectorSaved);

    doReturn(injectorContractSaved).when(injectorContractService).injectorContract(any());
    doReturn(new User()).when(userService).currentUser();
    doReturn(new ArrayList<>()).when(teamService).getTeamsByIds(any());
    doReturn(new ArrayList<>()).when(assetService).assets(any());
    doReturn(new HashSet<>()).when(tagService).tagSet(any());
    doReturn(null).when(documentService).document(any());
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
                injectorContractSaved.getFirstInjector().getId(),
                asset.getId());
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
            .keyType(ConditionKeyType.IPV4)
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
    assertEquals(
        ConditionKeyType.IPV4.name(),
        StepService.getField(stepTemplate.getInput(), "input.keyType"));
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
            .keyType(ConditionKeyType.IPV4)
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
            .keyType(ConditionKeyType.IPV4)
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
            .keyType(ConditionKeyType.IPV4)
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
      injectorRepository.deleteById(id);
    }

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
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());

    mapper.readValue(injectInputJson, InjectInput.class);
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    StepsCreateInput.StepInput step = InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .keyType(ConditionKeyType.IPV4)
            .value("output.message.ip")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));
    // ACT
    Optional<Step> stepTemplateOpt = injectExecutionStep.create(step, workflowTemplate);
    assertTrue(stepTemplateOpt.isPresent());
    Step stepTemplate = stepTemplateOpt.get();

    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());

    Optional<Step> stepReadyOpt =
        injectExecutionStep.ready(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
    assertTrue(stepReadyOpt.isPresent());
    Step stepReady = stepReadyOpt.get();

    String injectorId = StepService.getField(stepReady.getData(), "inject_injector");
    assertNotNull(injectorId);
    stepReady.setData(StepService.setField(stepReady.getData(), "inject_injector", ""));

    ChainingException ex =
        Assertions.assertThrows(ChainingException.class, () -> injectExecutionStep.run(stepReady));

    // ASSERT
    Assertions.assertEquals(
        "Injector not found for injectorContractId "
            + injectorContractSaved.getId()
            + " and step (READY) ID null",
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
            .keyType(ConditionKeyType.IPV4)
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
            .keyType(ConditionKeyType.IPV4)
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
        "{\"config\":{\"type\":\"openaev_implant\",\"expose\":true,\"label\":{\"en\":\"OpenAEV Implant\",\"fr\":\"OpenAEV Implant\"},\"color_dark\":\"#000000\",\"color_light\":\"#000000\"},\"label\":{\"en\":\"WHOAMI\",\"fr\":\"WHOAMI\"},\"manual\":false,\"fields\":[{\"key\":\"assets\",\"label\":\"Source assets\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":[\"assets\",\"asset_groups\"],\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"n\",\"defaultValue\":[],\"type\":\"asset\"},{\"key\":\"asset_groups\",\"label\":\"Source asset groups\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":[\"assets\",\"asset_groups\"],\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"n\",\"defaultValue\":[],\"type\":\"asset-group\"},{\"key\":\"obfuscator\",\"label\":\"Obfuscators\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":null,\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"1\",\"defaultValue\":[\"plain-text\"],\"choices\":[{\"label\":\"plain-text\",\"value\":\"plain-text\",\"information\":\"\"},{\"label\":\"base64\",\"value\":\"base64\",\"information\":\"CMD does not support base64 obfuscation\"}],\"type\":\"choice\"},{\"key\":\"expectations\",\"label\":\"Expectations\",\"mandatory\":false,\"readOnly\":false,\"mandatoryGroups\":null,\"mandatoryConditionFields\":null,\"mandatoryConditionValues\":null,\"visibleConditionFields\":null,\"visibleConditionValues\":null,\"linkedFields\":[],\"linkedValues\":[],\"cardinality\":\"n\",\"defaultValue\":[],\"predefinedExpectations\":[{\"expectation_type\":\"PREVENTION\",\"expectation_name\":\"Prevention\",\"expectation_description\":null,\"expectation_score\":100.0,\"expectation_expectation_group\":false,\"expectation_expiration_time\":21600},{\"expectation_type\":\"DETECTION\",\"expectation_name\":\"Detection\",\"expectation_description\":null,\"expectation_score\":100.0,\"expectation_expectation_group\":false,\"expectation_expiration_time\":21600}],\"type\":\"expectation\"}],\"variables\":[{\"key\":\"user\",\"label\":\"User that will receive the injection\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[{\"key\":\"user.id\",\"label\":\"Id of the user in the platform\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.email\",\"label\":\"Email of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.firstname\",\"label\":\"First name of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.lastname\",\"label\":\"Last name of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"user.lang\",\"label\":\"Language of the user\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]}]},{\"key\":\"exercise\",\"label\":\"Exercise of the current injection\",\"type\":\"Object\",\"cardinality\":\"1\",\"children\":[{\"key\":\"exercise.id\",\"label\":\"Id of the user in the platform\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"exercise.name\",\"label\":\"Name of the exercise\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"exercise.description\",\"label\":\"Description of the exercise\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]}]},{\"key\":\"teams\",\"label\":\"List of team name for the injection\",\"type\":\"String\",\"cardinality\":\"n\",\"children\":[]},{\"key\":\"player_uri\",\"label\":\"Player interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"challenges_uri\",\"label\":\"Challenges interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"scoreboard_uri\",\"label\":\"Scoreboard interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]},{\"key\":\"lessons_uri\",\"label\":\"Lessons learned interface platform link\",\"type\":\"String\",\"cardinality\":\"1\",\"children\":[]}],\"context\":{},\"contract_id\":\"73bfd988-b0bd-4740-bb7e-a6209a538835\",\"contract_attack_patterns_external_ids\":[],\"is_atomic_testing\":true,\"needs_executor\":true,\"platforms\":[\"MacOS\"],\"domains\":[{\"listened\":true,\"domain_id\":\"948e3cdc-c345-45dd-80cb-943804c09a3a\",\"domain_name\":\"Endpoint\",\"domain_color\":\"#389CFF\",\"domain_created_at\":\"2026-02-03T12:15:01.323228Z\",\"domain_updated_at\":\"2026-02-03T12:15:01.323228Z\"}]}");
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
}
