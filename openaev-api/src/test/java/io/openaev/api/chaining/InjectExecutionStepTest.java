package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetService;
import io.openaev.service.TeamService;
import io.openaev.service.UserService;
import io.openaev.service.chaining.StepService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.helpers.InjectTestHelper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class InjectExecutionStepTest {
  @Mock private InjectorContractService injectorContractService;
  @Mock private UserService userService;
  @Mock private TeamService teamService;
  @Mock private AssetService assetService;
  @Mock private TagService tagService;
  @Mock private DocumentService documentService;
  @Mock private InjectService injectService;
  @Mock private io.openaev.executors.Executor executor;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  InjectExecutionStep injectExecutionStep;
  ObjectMapper mapper = new ObjectMapper();
  @Autowired private InjectTestHelper injectTestHelper;
  String injectInputJson;
  String dataStepJsonToRun;
  String dataStepJsonToUpdate;

  @BeforeEach
  void beforeEach() throws Exception {
    this.injectExecutionStep =
        new InjectExecutionStep(
            injectorContractService,
            userService,
            assetService,
            teamService,
            tagService,
            documentService,
            injectService,
            null,
            null,
            null,
            executor,
            null);

    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    injectorRepository.save(injector);

    InjectorContract injectorContract = getInjectorContract();
    injectorContract.setInjector(injector);
    InjectorContract injectorContractSaved = injectorContractRepository.save(injectorContract);

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
                                                    "inject_injector_contract": "73bfd988-b0bd-4740-bb7e-a6209a538835",
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
            .formatted(asset.getId());

    dataStepJsonToRun =
        """
                           {
                                   "type": "inject",
                                   "inject_id" : "",
                                   "inject_title": "whoami",
                                   "inject_description": "",
                                   "inject_injector_contract": "73bfd988-b0bd-4740-bb7e-a6209a538835",
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
            .formatted(asset.getId());
    dataStepJsonToUpdate =
        """
                      {
                              "type": "inject",
                              "inject_id" : "INJECT-ID",
                              "inject_title": "whoami",
                              "inject_description": "",
                              "inject_injector_contract": "73bfd988-b0bd-4740-bb7e-a6209a538835",
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
            .formatted(asset.getId());
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
  public void createTest() throws JsonProcessingException {
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    Exercise simulation = new Exercise();
    StepsCreateInput.StepCreateInput step =
        InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);

    ConditionCreateInput conditionMapper =
        ConditionCreateInput.builder()
            .key("ip")
            .value("output.message.ip")
            .stepFrom("firstStep")
            .type(ConditionType.MAPPER)
            .build();
    step.setConditions(Collections.singletonList(conditionMapper));

    Workflow workflowTemplate =
        Workflow.builder()
            .status(WorkflowStatus.TEMPLATE)
            .version(0)
            .isEdited(false)
            .simulation(simulation)
            .workflowCreatedAt(Instant.now())
            .workflowUpdatedAt(Instant.now())
            .build();

    Step stepTemplate = injectExecutionStep.create(step, workflowTemplate);

    assertEquals(StepActionClass.INJECT_EXECUTION, stepTemplate.getStepAction());
    assertEquals(StepStatus.TEMPLATE, stepTemplate.getStatus());
    assertFalse(stepTemplate.getData().isEmpty());
    assertFalse(stepTemplate.getData().isBlank());
    assertEquals(
        "73bfd988-b0bd-4740-bb7e-a6209a538835",
        StepService.getField(
            stepTemplate.getData(), "inject_injector_contract.injector_contract_id"));
    assertEquals("output.message.ip", StepService.getField(stepTemplate.getInput(), "input.path"));
    assertEquals("firstStep", StepService.getField(stepTemplate.getInput(), "input.id_step_from"));
    assertEquals("ip", StepService.getField(stepTemplate.getInput(), "input.key"));
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
  public void readyTest() throws JsonProcessingException {

    mapper.readValue(injectInputJson, InjectInput.class);
    Exercise simulation = new Exercise();

    Workflow workflowTemplate =
        Workflow.builder()
            .status(WorkflowStatus.TEMPLATE)
            .version(0)
            .isEdited(false)
            .simulation(simulation)
            .workflowCreatedAt(Instant.now())
            .workflowUpdatedAt(Instant.now())
            .build();

    Step stepTemplate =
        Step.builder()
            .data(injectInputJson)
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .limitExecution(1)
            .workflow(workflowTemplate)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    Workflow workflowRun =
        Workflow.builder()
            .status(WorkflowStatus.RUN)
            .version(0)
            .isEdited(false)
            .simulation(simulation)
            .workflowCreatedAt(Instant.now())
            .workflowUpdatedAt(Instant.now())
            .build();

    Step stepReady =
        injectExecutionStep.ready(stepTemplate, "{\"input\" : \"do defined\"}", workflowRun);
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
  public void runTest() {

    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    Workflow savedWorkflowTemplate =
        workflowComposer
            .forWorkflow(workflowTemplate)
            .withWorkflowTemplate(
                workflowComposer
                    .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
                    .withSimulation(
                        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise())))
            .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();

    Exercise simulation = savedWorkflowTemplate.getSimulation();

    Step stepTemplate =
        Step.builder()
            .data(dataStepJsonToRun)
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(StepStatus.TEMPLATE)
            .limitExecution(1)
            .workflow(workflowTemplate)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    Workflow workflowRun =
        Workflow.builder()
            .status(WorkflowStatus.RUN)
            .version(0)
            .isEdited(false)
            .simulation(simulation)
            .workflowCreatedAt(Instant.now())
            .workflowUpdatedAt(Instant.now())
            .workflowTemplate(workflowTemplate)
            .build();

    Workflow savedWorkflowRun = workflowComposer.forWorkflow(workflowRun).persist().get();
    Step stepReady =
        injectExecutionStep.ready(stepTemplate, "{\"input\" : \"do defined\"}", savedWorkflowRun);
    stepReady = injectExecutionStep.run(stepReady);
    assertNotNull(StepService.getField(stepReady.getData(), "inject_id"));
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
  public void updateTest() {

    Exercise simulation = new Exercise();
    Workflow workflowRun =
        Workflow.builder()
            .status(WorkflowStatus.RUN)
            .version(0)
            .isEdited(false)
            .simulation(simulation)
            .workflowCreatedAt(Instant.now())
            .workflowUpdatedAt(Instant.now())
            .build();

    Step stepRun =
        Step.builder()
            .data(dataStepJsonToUpdate)
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(StepStatus.RUN)
            .limitExecution(1)
            .workflow(workflowRun)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    Step runUpdated = injectExecutionStep.update(stepRun);
    assertNotNull(StepService.getField(runUpdated.getOutput(), "outputs.agent_id"));
    assertEquals("testValue", StepService.getField(runUpdated.getOutput(), "outputs.message.test"));
  }

  private InjectorContract getInjectorContract() throws JsonProcessingException {
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
    injectorContract.setInjector(injector);
    injectorContract.setAtomicTesting(false);
    injectorContract.setCustom(false);
    injectorContract.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.MacOS});
    injectorContract.setNeedsExecutor(true);
    injectorContract.setImportAvailable(false);

    return injectorContract;
  }
}
