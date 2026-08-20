package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.BadRequestException;
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
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
class StepServiceIntegrationTest extends IntegrationTest {

  @MockitoSpyBean private StepService spyStepService;

  @Autowired private StepRepository stepRepository;
  @Autowired private ConditionService conditionService;
  @Autowired private ConditionRepository conditionRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer simulationComposer;
  @MockitoBean private InjectorContractService injectorContractService;
  @MockitoBean private UserService userService;
  @MockitoBean private TeamService teamService;
  @MockitoBean private AssetService assetService;
  @MockitoBean private TagService tagService;
  @MockitoBean private DocumentService documentService;
  @MockitoBean private InjectService injectService;
  @MockitoBean private io.openaev.executors.Executor executor;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectRepository injectRepository;
  ObjectMapper mapper = new ObjectMapper();
  @Autowired private InjectTestHelper injectTestHelper;
  String injectInputJson;
  InjectorContract injectorContractSaved;

  @BeforeEach
  void beforeEach() throws Exception {
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Injector injectorSaved = injectTestHelper.forceSaveInjector(injector);

    InjectorContract injectorContract = InjectorContractFixture.createImplantInjectorContract();
    injectorContract.addInjector(injectorSaved);
    injectorContractSaved = injectTestHelper.forceSaveInjectorContract(injectorContract);

    doReturn(injectorContractSaved).when(injectorContractService).injectorContract(any());
    doReturn(new User()).when(userService).currentUser();
    doReturn(new ArrayList<>()).when(teamService).getTeamsByIds(any());
    doReturn(new ArrayList<>()).when(assetService).assets(anyList());
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
    executionTrace.setStatus(ExecutionTraceStatus.EXECUTED);

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
    StepsCreateInput.StepInput input = buildInvalidInputCondition();
    input.setDataStep(injectInput);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> spyStepService.createStepTemplates(workflow, List.of(input)));

    // vérification du message
    assertEquals(
        "New step (TEMPLATE): Only 1 condition can be first parent", exception.getMessage());

    verify(spyStepService, atLeastOnce()).saveStep(any());
    assertTrue(TestTransaction.isFlaggedForRollback());
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
    StepsCreateInput.StepInput input1 = buildInvalidInput();
    input1.setDataStep(injectInput);

    StepsCreateInput.StepInput input2 = buildInvalidInput();
    input2.setDataStep(injectInput);

    long countBefore = stepRepository.count();

    spyStepService.createStepTemplates(workflow, List.of(input1, input2));

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

    StepsCreateInput.StepInput input1 = buildInvalidInput();
    input1.setDataStep(injectInput);
    StepsCreateInput.StepInput input2 = buildInvalidInput();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> spyStepService.createStepTemplates(workflow, List.of(input1, input2)));

    // vérification du message
    assertEquals(
        "Data step of new step (TEMPLATE) do not contain injector contract",
        exception.getMessage());

    verify(spyStepService, atLeastOnce()).saveStep(any());
    assertTrue(TestTransaction.isFlaggedForRollback());
  }

  @Test
  void should_update_a_step_whose_preserved_condition_tree_has_children()
      throws JsonProcessingException, ChainingException {
    // -- PREPARE --
    // A step gated by an event: createConditionTree links EVERY node of the tree to the step - the
    // root with is_root=true, its leaves with is_root=false.
    InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
    Workflow workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();
    StepsCreateInput.StepInput createInput = buildInvalidInput(); // root + one child
    createInput.setDataStep(injectInput);
    Step step = spyStepService.createStepTemplate(workflow, createInput);

    List<Condition> linked = conditionService.findAllConditionsByStepId(step.getId());
    String rootId =
        linked.stream()
            .filter(c -> c.getConditionParent() == null)
            .map(Condition::getId)
            .findFirst()
            .orElseThrow();
    assertEquals(2, linked.size(), "the tree's root AND its child are linked to the step");

    // -- EXECUTE --
    // Editing the action preserves that tree by naming its ROOT only, exactly as the logic map
    // does.
    // The child used to be unlinked and then deleted while its preserved parent still referenced
    // it, and the step merge that followed failed the save with ObjectDeletedException.
    StepInput updateInput =
        StepInput.builder()
            .workflowId(workflow.getId())
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .conditionIds(List.of(rootId))
            .dataStep(injectInput)
            .build();

    Step updated = spyStepService.updateStepTemplate(step.getId(), updateInput);

    // -- ASSERT --
    assertNotNull(updated);
    Condition survivingRoot = conditionRepository.findById(rootId).orElse(null);
    assertNotNull(survivingRoot, "the preserved event survives");
    assertEquals(1, survivingRoot.getConditionChildren().size(), "and keeps its child");
    // The step->root JOIN must survive too, not just the condition rows: the unconditional
    // step-side conditionSteps.clear() must never orphan-delete the link the caller asked to
    // preserve via conditionIds, or the updated step would be silently event-less. This is the
    // exact clear() + linkExistingConditionsToStep(preserved) mechanism the autonomous
    // updateInjectStepTemplateDataAndTrigger reuses to preserve a shared event on update (#7482).
    List<Condition> stillLinked = conditionService.findAllConditionsByStepId(updated.getId());
    assertTrue(
        stillLinked.stream().anyMatch(c -> rootId.equals(c.getId())),
        "the step stays linked to its preserved event root");
  }

  @Test
  void createStepTemplate_shouldReject_whenInjectorContractDoesNotExist()
      throws JsonProcessingException {
    // #7418: authoring a template step whose baked data references a contract that no longer exists
    // in the tenant is rejected with a 400 (BadRequestException), never persisted as a ghost step.
    // The existence check hits the real repository, so the mocked injectorContractService return
    // value does not mask the missing row.
    String missingContractId = "missing-" + UUID.randomUUID();
    InjectInput injectInput =
        mapper.readValue(
            injectInputJson.replace(injectorContractSaved.getId(), missingContractId),
            InjectInput.class);
    Workflow workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();
    StepsCreateInput.StepInput input = buildInvalidInput();
    input.setDataStep(injectInput);

    long countBefore = stepRepository.count();
    BadRequestException ex =
        assertThrows(
            BadRequestException.class, () -> spyStepService.createStepTemplate(workflow, input));
    assertTrue(ex.getMessage().contains(missingContractId));
    assertEquals(countBefore, stepRepository.count(), "no ghost step persisted");
  }

  @Test
  void createInjectStepTemplateIdempotent_shouldReject_whenInjectorContractDoesNotExist()
      throws JsonProcessingException {
    // #7418: the autonomous author path (idempotent) must reject a deleted contract too, so a stale
    // orchestrator cannot recreate the ghost-step state fixed by #7413.
    String missingContractId = "missing-" + UUID.randomUUID();
    InjectInput injectInput =
        mapper.readValue(
            injectInputJson.replace(injectorContractSaved.getId(), missingContractId),
            InjectInput.class);
    Workflow workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();
    StepsCreateInput.StepInput input = new StepsCreateInput.StepInput();
    input.setStepAction(StepActionClass.INJECT_EXECUTION);
    input.setDataStep(injectInput);

    long countBefore = stepRepository.count();
    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () -> spyStepService.createInjectStepTemplateIdempotent(workflow, input, null));
    assertTrue(ex.getMessage().contains(missingContractId));
    assertEquals(countBefore, stepRepository.count(), "no ghost step persisted");
  }

  private StepsCreateInput.StepInput buildInvalidInputCondition() {

    StepsCreateInput.StepInput stepInput = new StepsCreateInput.StepInput();
    stepInput.setStepAction(StepActionClass.INJECT_EXECUTION);
    stepInput.setDataStep(new InjectInput());
    ConditionCreateInput root1 = new ConditionCreateInput();
    root1.setTemporaryId("tmp-1");
    root1.setTemporaryIdConditionParent(null); // root
    root1.setType(ConditionType.EQ);
    root1.setKeyTypes(List.of(PrimitiveType.Text));
    root1.setValue("A");

    ConditionCreateInput root2 = new ConditionCreateInput();
    root2.setTemporaryId("tmp-2");
    root2.setTemporaryIdConditionParent(null); // second root → BOOM
    root2.setType(ConditionType.EQ);
    root2.setKeyTypes(List.of(PrimitiveType.Text));
    root2.setValue("B");

    stepInput.setConditions(List.of(root1, root2));
    return stepInput;
  }

  private StepsCreateInput.StepInput buildInvalidInput() {

    StepsCreateInput.StepInput stepInput = new StepsCreateInput.StepInput();
    stepInput.setStepAction(StepActionClass.INJECT_EXECUTION);
    stepInput.setDataStep(new InjectInput());
    ConditionCreateInput root1 = new ConditionCreateInput();
    root1.setTemporaryId("tmp-1");
    root1.setTemporaryIdConditionParent(null); // root
    root1.setType(ConditionType.EQ);
    root1.setKeyTypes(List.of(PrimitiveType.Text));
    root1.setValue("A");

    ConditionCreateInput root2 = new ConditionCreateInput();
    root2.setTemporaryId("tmp-2");
    root2.setTemporaryIdConditionParent("tmp-1"); // root
    root2.setType(ConditionType.EQ);
    root2.setKeyTypes(List.of(PrimitiveType.Text));
    root2.setValue("B");

    stepInput.setConditions(List.of(root1, root2));
    return stepInput;
  }
}
