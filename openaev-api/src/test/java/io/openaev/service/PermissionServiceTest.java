package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.aop.AccessControlAspect;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.EvaluationRepository;
import io.openaev.database.repository.ObjectiveRepository;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.service.chaining.ConditionService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.web.bind.annotation.RequestMethod;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class PermissionServiceTest extends IntegrationTest {
  private static final String RESOURCE_ID = "resourceid";
  private static final String USER_ID = "userid";
  private static final String SIMULATION_ID = "simulationId";
  private static final String CONDITION_WORKFLOW_ID = "conditionWorkflowId";

  @Mock private GrantService grantService;
  @Mock private InjectService injectService;
  @Mock private ObjectiveRepository objectiveRepository;
  @Mock private EvaluationRepository evaluationRepository;
  @Mock private WorkflowService workflowService;
  @Mock private StepService stepService;
  @Mock private ConditionService conditionService;
  @Mock private InjectorContractService injectorContractService;

  @InjectMocks private PermissionService permissionService;

  @BeforeEach
  public void setupChainingMocks() {
    Exercise simulation = mock(Exercise.class);
    when(simulation.getId()).thenReturn(SIMULATION_ID);

    Workflow workflow = mock(Workflow.class);
    when(workflow.getSimulation()).thenReturn(simulation);
    when(workflow.getScenario()).thenReturn(null);
    when(workflowService.findById(RESOURCE_ID)).thenReturn(workflow);

    Step step = mock(Step.class);
    when(step.getWorkflow()).thenReturn(workflow);
    when(stepService.findById(RESOURCE_ID)).thenReturn(step);

    Condition condition = mock(Condition.class);
    when(condition.getWorkflowId()).thenReturn(CONDITION_WORKFLOW_ID);
    when(conditionService.findConditionRootById(RESOURCE_ID)).thenReturn(condition);
    when(workflowService.findById(CONDITION_WORKFLOW_ID)).thenReturn(workflow);
  }

  @Test
  public void test_hasPermission_WHEN_admin() {
    assertTrue(
        permissionService.hasPermission(
            getUser(USER_ID, true),
            Optional.empty(),
            RESOURCE_ID,
            ResourceType.SCENARIO,
            Action.WRITE));
  }

  @Test
  public void test_hasPermission_objective_WHEN_has_grant() {
    String objectiveId = "objectiveId";
    Objective objective = mock(Objective.class);
    when(objective.getParentResourceId()).thenReturn(RESOURCE_ID);
    when(objective.getParentResourceType()).thenReturn(ResourceType.SIMULATION);
    User user = getUser(USER_ID, false);
    when(grantService.hasReadGrant(RESOURCE_ID, user)).thenReturn(true);
    when(objectiveRepository.findById(objectiveId)).thenReturn(Optional.of(objective));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), objectiveId, ResourceType.OBJECTIVE, Action.READ));
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), objectiveId, ResourceType.OBJECTIVE, Action.WRITE));
  }

  @Test
  public void test_hasPermission_evaluation_WHEN_has_grant() {
    String evaluationId = "evaluationId";
    Evaluation evaluation = mock(Evaluation.class);
    when(evaluation.getParentResourceId()).thenReturn(RESOURCE_ID);
    when(evaluation.getParentResourceType()).thenReturn(ResourceType.SCENARIO);
    User user = getUser(USER_ID, false);
    when(grantService.hasReadGrant(RESOURCE_ID, user)).thenReturn(true);
    when(evaluationRepository.findById(evaluationId)).thenReturn(Optional.of(evaluation));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), evaluationId, ResourceType.EVALUATION, Action.READ));
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), evaluationId, ResourceType.EVALUATION, Action.WRITE));
  }

  @Test
  public void test_hasPermission_read_WHEN_has_read_grant() {
    User user = getUser(USER_ID, false);
    when(grantService.hasReadGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.READ));
  }

  @Test
  public void test_hasPermission_write_WHEN_has_write_grant() {
    User user = getUser(USER_ID, false);
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.SIMULATION, Action.WRITE));
  }

  @Test
  public void test_hasPermission_write_WHEN_has_no_grant_but_Capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.ACCESS_ASSESSMENT)));
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(false);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.SIMULATION, Action.READ));
  }

  @Test
  public void test_hasPermission_delete_WHEN_has_write_grant() {
    User user = getUser(USER_ID, false);
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.SIMULATION, Action.DELETE));
  }

  @Test
  public void test_hasPermission_launch_WHEN_has_launch_grant() {
    User user = getUser(USER_ID, false);
    when(grantService.hasLaunchGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.LAUNCH));
  }

  @Test
  public void test_hasPermission_search_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.SEARCH));
  }

  @Test
  public void test_hasPermission_read_WHEN_has_read_capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.ACCESS_CHANNELS)));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.CHANNEL, Action.READ));
  }

  @Test
  public void test_hasPermission_read_WHEN_has_bypass_capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.BYPASS)));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.CHANNEL, Action.READ));
  }

  @Test
  public void test_hasPermission_search_payload_WHEN_has_tenant_bypass_capa() {
    // Regression for #6331 / #6332: a CE admin's authority is a default-tenant BYPASS group, so a
    // tenant BYPASS must cover /api/payloads/** like every other tenant resource. ACCESS_PAYLOADS
    // previously had an empty capability scope, leaving payloads unreachable via BYPASS (403).
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.BYPASS)));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.PAYLOAD, Action.SEARCH));
  }

  @Test
  public void test_hasPermission_write_WHEN_has_read_capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.ACCESS_CHANNELS)));
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.CHANNEL, Action.WRITE));
  }

  @Test
  public void test_hasPermission_read_player_WHEN_has_no_capa() {
    User user = getUser(USER_ID, false);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.PLAYER, Action.READ));
  }

  @Test
  public void test_hasPermission_read_team_WHEN_has_no_capa() {
    User user = getUser(USER_ID, false);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.TEAM, Action.READ));
  }

  @Test
  public void test_hasPermission_write_player_WHEN_has_no_capa() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.PLAYER, Action.WRITE));
  }

  @Test
  public void test_hasPermission_write_team_WHEN_has_no_capa() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.TEAM, Action.WRITE));
  }

  @Test
  public void test_hasPermission_create_WHEN_has_create_capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.MANAGE_ASSESSMENT)));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.CREATE));
  }

  @Test
  public void test_hasPermission_duplicate_WHEN_has_manage_capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.MANAGE_ASSESSMENT)));
    when(grantService.hasReadGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.DUPLICATE));
  }

  @Test
  public void test_hasPermission_create_WHEN_has_no_capa() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.SCENARIO, Action.CREATE));
  }

  @Test
  public void test_hasPermission_write_inject_WHEN_has_write_grant() {
    String injectId = "injectId";
    Inject inject = mock(Inject.class);
    when(inject.getParentResourceId()).thenReturn(RESOURCE_ID);
    when(inject.getParentResourceType()).thenReturn(ResourceType.SIMULATION);
    when(injectService.inject(injectId)).thenReturn(inject);

    User user = getUser(USER_ID, false);
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), injectId, ResourceType.INJECT, Action.WRITE));
  }

  @Test
  public void test_hasPermission_write_inject_WHEN_has_no_grant() {
    String injectId = "injectId";
    Inject inject = mock(Inject.class);
    when(inject.getParentResourceId()).thenReturn(RESOURCE_ID);
    when(inject.getParentResourceType()).thenReturn(ResourceType.SIMULATION);
    when(injectService.inject(injectId)).thenReturn(inject);

    User user = getUser(USER_ID, false);
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), injectId, ResourceType.INJECT, Action.WRITE));
  }

  @Test
  public void test_hasPermission_search_user_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.USER, Action.SEARCH));
  }

  @Test
  public void test_hasPermission_duplicate_user_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.USER, Action.DUPLICATE));
  }

  @Test
  public void test_hasPermission_read_user_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.USER, Action.READ));
  }

  @Test
  public void test_hasPermission_write_user_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.USER, Action.WRITE));
  }

  @Test
  public void test_hasPermission_create_user_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.USER, Action.CREATE));
  }

  @Test
  public void test_hasPermission_search_options_WHEN_has_write_grant() {
    String injectId = "injectId";
    Inject inject = mock(Inject.class);
    when(inject.getParentResourceId()).thenReturn(RESOURCE_ID);
    when(inject.getParentResourceType()).thenReturn(ResourceType.SIMULATION);
    when(injectService.inject(injectId)).thenReturn(inject);

    User user = getUser(USER_ID, false);
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(true);
    AccessControlAspect.HttpMappingInfo mappingInfo =
        new AccessControlAspect.HttpMappingInfo(
            RequestMethod.GET, new String[] {"api/injector/options"}, Map.of("sourceId", injectId));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.of(mappingInfo), null, ResourceType.INJECTOR, Action.SEARCH));
  }

  @Test
  public void test_hasPermission_read_workflow_step_WHEN_has_no_permission() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.WORKFLOW, Action.READ));
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.STEP, Action.READ));
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.CONDITION, Action.READ));
  }

  @Test
  public void test_hasPermission_update_workflow_step_WHEN_has_no_permission() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.WORKFLOW, Action.WRITE));
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.STEP, Action.WRITE));
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.CONDITION, Action.WRITE));
  }

  @Test
  public void test_hasPermission_delete_workflow_step_WHEN_has_no_permission() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.WORKFLOW, Action.DELETE));
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.STEP, Action.DELETE));
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.CONDITION, Action.DELETE));
  }

  @Test
  public void test_hasPermission_read_workflow_step_WHEN_has_access_assessment() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.ACCESS_ASSESSMENT)));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.WORKFLOW, Action.READ));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.STEP, Action.READ));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.CONDITION, Action.READ));
  }

  @Test
  public void test_hasPermission_update_workflow_step_WHEN_has_manage_assessment() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.MANAGE_ASSESSMENT)));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.WORKFLOW, Action.WRITE));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.STEP, Action.WRITE));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.CONDITION, Action.WRITE));
  }

  @Test
  public void test_hasPermission_delete_workflow_step_WHEN_has_manage_assessment() {
    // DELETE on workflow/step/condition maps to WRITE on the parent (simulation/scenario),
    // so MANAGE_ASSESSMENT (which grants WRITE on simulation/scenario) is required.
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.MANAGE_ASSESSMENT)));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.WORKFLOW, Action.DELETE));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.STEP, Action.DELETE));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), RESOURCE_ID, ResourceType.CONDITION, Action.DELETE));
  }

  public void given_workflowWithParentSimulation_should_allowAccessWhenGranted() {
    String workflowId = "workflowId";
    String simulationId = "simulationId";

    Exercise simulation = mock(Exercise.class);
    when(simulation.getId()).thenReturn(simulationId);

    Workflow workflow = mock(Workflow.class);
    when(workflow.getSimulation()).thenReturn(simulation);
    when(workflow.getScenario()).thenReturn(null);
    when(workflowService.findById(workflowId)).thenReturn(workflow);

    User user = getUser(USER_ID, false);
    when(grantService.hasReadGrant(simulationId, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), workflowId, ResourceType.WORKFLOW, Action.READ));

    when(grantService.hasWriteGrant(simulationId, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), workflowId, ResourceType.WORKFLOW, Action.WRITE));
  }

  @Test
  public void given_stepWithParentScenarioViaWorkflow_should_allowAccessWhenGranted() {
    String stepId = "stepId";
    String scenarioId = "scenarioId";

    Scenario scenario = mock(Scenario.class);
    when(scenario.getId()).thenReturn(scenarioId);

    Workflow workflow = mock(Workflow.class);
    when(workflow.getSimulation()).thenReturn(null);
    when(workflow.getScenario()).thenReturn(scenario);

    Step step = mock(Step.class);
    when(step.getWorkflow()).thenReturn(workflow);
    when(stepService.findById(stepId)).thenReturn(step);

    User user = getUser(USER_ID, false);
    when(grantService.hasReadGrant(scenarioId, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), stepId, ResourceType.STEP, Action.READ));

    when(grantService.hasWriteGrant(scenarioId, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), stepId, ResourceType.STEP, Action.WRITE));
  }

  @Test
  public void given_conditionWithParentSimulationViaWorkflow_should_allowAccessWhenGranted() {
    String conditionId = "conditionId";
    String workflowId = "workflowId";
    String simulationId = "simulationId";

    Exercise simulation = mock(Exercise.class);
    when(simulation.getId()).thenReturn(simulationId);

    Workflow workflow = mock(Workflow.class);
    when(workflow.getSimulation()).thenReturn(simulation);
    when(workflow.getScenario()).thenReturn(null);

    Condition condition = mock(Condition.class);
    when(condition.getWorkflowId()).thenReturn(workflowId);
    when(conditionService.findConditionRootById(conditionId)).thenReturn(condition);
    when(workflowService.findById(workflowId)).thenReturn(workflow);

    User user = getUser(USER_ID, false);
    when(grantService.hasReadGrant(simulationId, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), conditionId, ResourceType.CONDITION, Action.READ));

    when(grantService.hasWriteGrant(simulationId, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, Optional.empty(), conditionId, ResourceType.CONDITION, Action.WRITE));
  }

  private User getUser(final String id, final boolean isAdmin) {
    User user = UserFixture.getUser();
    user.setAdmin(isAdmin);
    user.setId(id);
    return user;
  }

  private Group getGroup(final Capability capability) {
    Set<Capability> capabilities = new HashSet<>();
    capabilities.add(capability);
    Role role = new Role();
    role.setId("testid");
    role.setCapabilities(capabilities);
    List<Role> roles = new ArrayList<>();
    roles.add(role);
    Group group = new Group();
    group.setId("testid");
    group.setRoles(roles);
    group.setTenant(entityManager.getReference(Tenant.class, TenantContext.getCurrentTenant()));
    return group;
  }
}
