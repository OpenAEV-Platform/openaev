package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Action;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Inject;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.utils.fixtures.UserFixture;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class PermissionServiceTest extends IntegrationTest {
  private static final String RESOURCE_ID = "resourceid";
  private static final String USER_ID = "userid";

  @Mock private GrantService grantService;
  @Mock private InjectService injectService;

  @InjectMocks private PermissionService permissionService;

  @Test
  public void test_hasPermission_WHEN_admin() {
    assertTrue(
        permissionService.hasPermission(
            getUser(USER_ID, true), RESOURCE_ID, ResourceType.SCENARIO, Action.WRITE));
  }

  @Test
  public void test_hasPermission_read_WHEN_has_read_grant() {
    User user = getUser(USER_ID, false);
    when(grantService.hasReadGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.SCENARIO, Action.READ));
  }

  @Test
  public void test_hasPermission_write_WHEN_has_write_grant() {
    User user = getUser(USER_ID, false);
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.SIMULATION, Action.WRITE));
  }

  @Test
  public void test_hasPermission_write_WHEN_has_no_grant_but_Capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.ACCESS_ASSESSMENT)));
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(false);
    assertTrue(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.SIMULATION, Action.READ));
  }

  @Test
  public void test_hasPermission_delete_WHEN_has_write_grant() {
    User user = getUser(USER_ID, false);
    when(grantService.hasWriteGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.SIMULATION, Action.DELETE));
  }

  @Test
  public void test_hasPermission_launch_WHEN_has_launch_grant() {
    User user = getUser(USER_ID, false);
    when(grantService.hasLaunchGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.SCENARIO, Action.LAUNCH));
  }

  @Test
  public void test_hasPermission_search_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertTrue(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.SCENARIO, Action.SEARCH));
  }

  @Test
  public void test_hasPermission_read_WHEN_has_read_capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.ACCESS_CHANNELS)));
    assertTrue(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.CHANNEL, Action.READ));
  }

  @Test
  public void test_hasPermission_read_WHEN_has_bypass_capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.BYPASS)));
    assertTrue(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.CHANNEL, Action.READ));
  }

  @Test
  public void test_hasPermission_write_WHEN_has_read_capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.ACCESS_CHANNELS)));
    assertFalse(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.CHANNEL, Action.WRITE));
  }

  @Test
  public void test_hasPermission_read_player_WHEN_has_no_capa() {
    User user = getUser(USER_ID, false);
    assertTrue(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.PLAYER, Action.READ));
  }

  @Test
  public void test_hasPermission_read_team_WHEN_has_no_capa() {
    User user = getUser(USER_ID, false);
    assertTrue(permissionService.hasPermission(user, RESOURCE_ID, ResourceType.TEAM, Action.READ));
  }

  @Test
  public void test_hasPermission_write_player_WHEN_has_no_capa() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.PLAYER, Action.WRITE));
  }

  @Test
  public void test_hasPermission_write_team_WHEN_has_no_capa() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.TEAM, Action.WRITE));
  }

  @Test
  public void test_hasPermission_create_WHEN_has_create_capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.MANAGE_ASSESSMENT)));
    assertTrue(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.SCENARIO, Action.CREATE));
  }

  @Test
  public void test_hasPermission_duplicate_WHEN_has_manage_capa() {
    User user = getUser(USER_ID, false);
    user.setGroups(List.of(getGroup(Capability.MANAGE_ASSESSMENT)));
    when(grantService.hasReadGrant(RESOURCE_ID, user)).thenReturn(true);
    assertTrue(
        permissionService.hasPermission(
            user, RESOURCE_ID, ResourceType.SCENARIO, Action.DUPLICATE));
  }

  @Test
  public void test_hasPermission_create_WHEN_has_no_capa() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.SCENARIO, Action.CREATE));
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
    assertTrue(permissionService.hasPermission(user, injectId, ResourceType.INJECT, Action.WRITE));
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
    assertFalse(permissionService.hasPermission(user, injectId, ResourceType.INJECT, Action.WRITE));
  }

  @Test
  public void test_hasPermission_search_user_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.USER, Action.SEARCH));
  }

  @Test
  public void test_hasPermission_duplicate_user_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.USER, Action.DUPLICATE));
  }

  @Test
  public void test_hasPermission_read_user_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertFalse(permissionService.hasPermission(user, RESOURCE_ID, ResourceType.USER, Action.READ));
  }

  @Test
  public void test_hasPermission_write_user_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.USER, Action.WRITE));
  }

  @Test
  public void test_hasPermission_create_user_WHEN_has_no_grant() {
    User user = getUser(USER_ID, false);
    assertFalse(
        permissionService.hasPermission(user, RESOURCE_ID, ResourceType.USER, Action.CREATE));
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
