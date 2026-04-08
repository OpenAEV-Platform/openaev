package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetService;
import io.openaev.service.TeamService;
import io.openaev.service.UserService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.helpers.InjectTestHelper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

@SpringBootTest
class StepServiceIntegrationTest {

  @Autowired private StepService stepService;

  @Autowired private StepRepository stepRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer simulationComposer;
  @MockBean private InjectorContractService injectorContractService;
  @MockBean private UserService userService;
  @MockBean private TeamService teamService;
  @MockBean private AssetService assetService;
  @MockBean private TagService tagService;
  @MockBean private DocumentService documentService;
  @MockBean private InjectService injectService;
  @MockBean private io.openaev.executors.Executor executor;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectRepository injectRepository;
  ObjectMapper mapper = new ObjectMapper();
  @Autowired private InjectTestHelper injectTestHelper;
  String injectInputJson;
  InjectorContract injectorContractSaved;
  @SpyBean private StepService spyStepService;

  @BeforeEach
  void beforeEach() throws Exception {
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Injector injectorSaved = injectorRepository.save(injector);

    InjectorContract injectorContract = InjectorContractFixture.createImplantInjectorContract();
    injectorContract.addInjector(injectorSaved);
    injectorContractSaved = injectorContractRepository.save(injectorContract);
    // Link on the owning side and save to persist the join table
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
            .formatted(injectorContractSaved.getId(), asset.getId());
  }

  @Test
  void should_rollback_when_condition_fails() throws JsonProcessingException {
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    Workflow workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();
    String workflowId = workflow.getId();

    StepsCreateInput.StepCreateInput input = buildInvalidInputCondition();
    input.setDataStep(injectInput);

    long countBefore = stepRepository.count();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> stepService.createStepTemplates(workflowId, List.of(input)));

    // vérification du message
    assertEquals(
        "New step (TEMPLATE): Only 1 condition can be first parent", exception.getMessage());

    verify(spyStepService, atLeastOnce()).saveStep(any());
    long countAfter = stepRepository.count();
    assertEquals(countBefore, countAfter);
  }

  @Test
  void should_success_when_condition_valid() throws JsonProcessingException, ChainingException {
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    Workflow workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();
    String workflowId = workflow.getId();

    StepsCreateInput.StepCreateInput input1 = buildInvalidInput();
    input1.setDataStep(injectInput);

    StepsCreateInput.StepCreateInput input2 = buildInvalidInput();
    input2.setDataStep(injectInput);

    long countBefore = stepRepository.count();

    stepService.createStepTemplates(workflowId, List.of(input1, input2));

    // vérification du message

    verify(spyStepService, atLeastOnce()).saveStep(any());
    long countAfter = stepRepository.count();
    assertNotEquals(countBefore, countAfter);
    assertEquals(2, countAfter - countBefore);
  }

  @Test
  void should_rollback_when_second_step_fails() throws JsonProcessingException {
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    Workflow workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();
    String workflowId = workflow.getId();

    StepsCreateInput.StepCreateInput input1 = buildInvalidInput();
    input1.setDataStep(injectInput);
    StepsCreateInput.StepCreateInput input2 = buildInvalidInput();

    long countBefore = stepRepository.count();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> stepService.createStepTemplates(workflowId, List.of(input1, input2)));

    // vérification du message
    assertEquals(
        "Data step of new step (TEMPLATE) do not contain injector contract",
        exception.getMessage());

    verify(spyStepService, atLeastOnce()).saveStep(any());
    long countAfter = stepRepository.count();
    assertEquals(countBefore, countAfter);
  }

  private StepsCreateInput.StepCreateInput buildInvalidInputCondition() {

    StepsCreateInput.StepCreateInput stepInput = new StepsCreateInput.StepCreateInput();
    stepInput.setStepAction(StepActionClass.INJECT_EXECUTION);
    stepInput.setDataStep(new InjectInput());
    ConditionCreateInput root1 = new ConditionCreateInput();
    root1.setTemporaryId("tmp-1");
    root1.setTemporaryIdConditionParent(null); // root
    root1.setType(ConditionType.EQ);
    root1.setKey("status");
    root1.setValue("A");
    root1.setStepFrom(null);

    ConditionCreateInput root2 = new ConditionCreateInput();
    root2.setTemporaryId("tmp-2");
    root2.setTemporaryIdConditionParent(null); // second root → BOOM
    root2.setType(ConditionType.EQ);
    root2.setKey("status");
    root2.setValue("B");
    root2.setStepFrom(null);

    stepInput.setConditions(List.of(root1, root2));
    return stepInput;
  }

  private StepsCreateInput.StepCreateInput buildInvalidInput() {

    StepsCreateInput.StepCreateInput stepInput = new StepsCreateInput.StepCreateInput();
    stepInput.setStepAction(StepActionClass.INJECT_EXECUTION);
    stepInput.setDataStep(new InjectInput());
    ConditionCreateInput root1 = new ConditionCreateInput();
    root1.setTemporaryId("tmp-1");
    root1.setTemporaryIdConditionParent(null); // root
    root1.setType(ConditionType.EQ);
    root1.setKey("status");
    root1.setValue("A");
    root1.setStepFrom(null);

    ConditionCreateInput root2 = new ConditionCreateInput();
    root2.setTemporaryId("tmp-2");
    root2.setTemporaryIdConditionParent("tmp-1"); // root
    root2.setType(ConditionType.EQ);
    root2.setKey("status");
    root2.setValue("B");
    root2.setStepFrom(null);

    stepInput.setConditions(List.of(root1, root2));
    return stepInput;
  }
}
