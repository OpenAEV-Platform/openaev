package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.aop.RBACAspect;
import io.openaev.database.model.*;
import io.openaev.database.repository.EvaluationRepository;
import io.openaev.database.repository.ObjectiveRepository;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.*;
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

  @Mock private GrantService grantService;
  @Mock private InjectService injectService;
  @Mock private ObjectiveRepository objectiveRepository;
  @Mock private EvaluationRepository evaluationRepository;

  @InjectMocks private PermissionService permissionService;

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
    RBACAspect.HttpMappingInfo mappingInfo =
        new RBACAspect.HttpMappingInfo(
            RequestMethod.GET, new String[] {"api/injector/options"}, Map.of("sourceId", injectId));
    assertTrue(
        permissionService.hasPermission(
            user, Optional.of(mappingInfo), null, ResourceType.INJECTOR, Action.SEARCH));
  }

  @Test
  public void test_hasNOPermission_search_options_WHEN_has_write_grant() {
    String injectId = "injectId";
    Inject inject = mock(Inject.class);
    when(inject.getParentResourceId()).thenReturn(RESOURCE_ID);
    when(inject.getParentResourceType()).thenReturn(ResourceType.SIMULATION);
    when(injectService.inject(injectId)).thenReturn(inject);

    User user = getUser(USER_ID, false);
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(true);
    assertFalse(
        permissionService.hasPermission(
            user, Optional.empty(), null, ResourceType.INJECTOR, Action.SEARCH));
  }

  @Test
  public void
      test_hasNOPermission_search_options_WHEN_has_write_grant_AND_incomplete_mapping_info_no_sourceId() {
    String injectId = "injectId";
    Inject inject = mock(Inject.class);
    when(inject.getParentResourceId()).thenReturn(RESOURCE_ID);
    when(inject.getParentResourceType()).thenReturn(ResourceType.SIMULATION);
    when(injectService.inject(injectId)).thenReturn(inject);

    User user = getUser(USER_ID, false);
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(true);
    RBACAspect.HttpMappingInfo mappingInfo =
        new RBACAspect.HttpMappingInfo(
            RequestMethod.GET, new String[] {"api/injector/options"}, Map.of());
    assertFalse(
        permissionService.hasPermission(
            user, Optional.of(mappingInfo), null, ResourceType.INJECTOR, Action.SEARCH));
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
    return group;
  }
}
