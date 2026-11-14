package io.openaev.rest.group;

import static io.openaev.api.groups.PlatformGroupApi.PLATFORM_GROUPS_URI;
import static io.openaev.api.groups.TenantGroupApi.TENANT_GROUP_URI;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.groups.PlatformGroupUpdateRolesInput;
import io.openaev.api.groups.PlatformGroupUpdateUsersInput;
import io.openaev.api.groups.dto.PlatformGroupInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.UserService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The privilege checks of the group endpoints read the roles of the group they change. With {@code
 * roles} among the active tables, a role of another tenant linked to the group is a row the request
 * scope hides, so the group reads with fewer roles than {@code groups_roles} holds and the check
 * compares the caller with a partial list. Such a link is not created by the API; the rows here are
 * written by JDBC, as a seed, an import or a legacy row would write them.
 *
 * <p>NOT {@code @Transactional}: the data is committed and the callers are authenticated the way
 * {@code TokenAuthenticationFilter} does it, so every request runs in the scoped transaction of its
 * own route.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Group privilege checks with a role the request scope hides")
class GroupPrivilegeCheckHiddenRolesTest extends IntegrationTest {

  /** A capability of the hidden role, held by none of the callers. */
  private static final Capability HIDDEN_CAPABILITY = Capability.ACCESS_ASSESSMENT;

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    // The production list with roles on it, as the activation will have it
    registry.add(
        "openaev.tenant.active-tables",
        () ->
            String.join(
                ",",
                Stream.concat(ProductionActiveTables.names().stream(), Stream.of("roles"))
                    .distinct()
                    .toList()));
  }

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantMembershipCacheManager membershipCache;
  @Autowired private JdbcTemplate jdbc;

  private String tenantA;
  private String tenantB;
  private User tenantCaller;
  private User platformCaller;
  private User platformCallerMemberOfA;
  private User tenantAdmin;
  private User tenantBypass;
  private User platformAdmin;
  private User platformBypass;
  private User member;
  private final List<String> roleIds = new ArrayList<>();
  private final List<String> groupIds = new ArrayList<>();
  private final List<String> userIds = new ArrayList<>();

  @BeforeAll
  void seed() throws Exception {
    userService.createAdminSession();
    try {
      tenantA = tenantHelper.createTenant("group-hidden-a").getId();
      tenantB = tenantHelper.createTenant("group-hidden-b").getId();
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clearCurrentTenant();
    }

    tenantCaller = newUser("tenant-caller");
    platformCaller = newUser("platform-caller");
    platformCallerMemberOfA = newUser("platform-caller-member-of-a");
    member = newUser("member");
    tenantAdmin = newAdmin("tenant-admin");
    platformAdmin = newAdmin("platform-admin");
    tenantBypass = newUser("tenant-bypass");
    platformBypass = newUser("platform-bypass");

    tenantTx.executeAsPlatformWrite(
        TxCtx.forTenants(List.of(tenantA, tenantB)),
        () -> {
          tenantCaller
              .getUnscopedGroups()
              .add(group(tenantB, role(tenantB, Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES)));
          platformCaller
              .getUnscopedGroups()
              .add(group(null, role(null, Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES)));
          platformCallerMemberOfA
              .getUnscopedGroups()
              .add(group(null, role(null, Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES)));
          tenantBypass.getUnscopedGroups().add(group(tenantB, role(tenantB, Capability.BYPASS)));
          platformBypass.getUnscopedGroups().add(group(null, role(null, Capability.BYPASS)));
          userRepository.saveAll(
              List.of(
                  tenantCaller,
                  platformCaller,
                  platformCallerMemberOfA,
                  tenantBypass,
                  platformBypass));
        });

    membership(tenantCaller, tenantB);
    membership(platformCallerMemberOfA, tenantA);
    membership(member, tenantB);
    membership(tenantAdmin, tenantB);
    membership(tenantBypass, tenantB);
    TenantContext.clearCurrentTenant();
  }

  @AfterAll
  void cleanup() {
    for (String userId : userIds) {
      jdbc.update("DELETE FROM users_groups WHERE user_id = ?", userId);
    }
    for (String groupId : groupIds) {
      jdbc.update("DELETE FROM users_groups WHERE group_id = ?", groupId);
      jdbc.update("DELETE FROM groups_roles WHERE group_id = ?", groupId);
      jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
    }
    // The roles include platform roles, written only in a platform-write transaction
    inOwnTransaction(
        "SELECT set_config('app.platform_write', 'true', true)",
        writer -> {
          for (String roleId : roleIds) {
            writer.update("DELETE FROM roles_capabilities WHERE role_id = ?", roleId);
            writer.update("DELETE FROM roles WHERE role_id = ?", roleId);
          }
        });
    for (String userId : userIds) {
      jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  private User newUser(String label) {
    User user =
        userRepository.save(
            UserFixture.getUser(
                label, label, label + "-" + UUID.randomUUID() + "@unittests.invalid", false));
    userIds.add(user.getId());
    return user;
  }

  private User newAdmin(String label) {
    User user =
        userRepository.save(
            UserFixture.getUser(
                label, label, label + "-" + UUID.randomUUID() + "@unittests.invalid", true));
    userIds.add(user.getId());
    return user;
  }

  /** A role of the tenant, or a platform role when {@code tenantId} is null. */
  private Role role(String tenantId, Capability capability) {
    Role role = new Role();
    role.setName("role-" + UUID.randomUUID());
    role.setCapabilities(Set.of(capability));
    if (tenantId != null) {
      role.setTenant(new Tenant(tenantId));
    }
    Role saved = roleRepository.save(role);
    roleIds.add(saved.getId());
    return saved;
  }

  /** A group of the tenant, or a platform group when {@code tenantId} is null. */
  private Group group(String tenantId, Role... roles) {
    Group group = new Group();
    group.setName("group-" + UUID.randomUUID());
    if (tenantId != null) {
      group.setTenant(new Tenant(tenantId));
    }
    group.getRoles().addAll(List.of(roles));
    Group saved = groupRepository.save(group);
    groupIds.add(saved.getId());
    return saved;
  }

  /**
   * The group, committed, with a role of tenant A linked by JDBC. The link is written with the
   * triggers skipped ({@code session_replication_role = replica}), as a restore would write it: the
   * database guard refuses a link between a role and a group of different tenants.
   */
  private String groupWithHiddenRole(String groupTenantId) {
    String hiddenRoleId = committedRole(tenantA, HIDDEN_CAPABILITY);
    String groupId =
        tenantTx.executeAsPlatformWrite(
            TxCtx.forTenants(List.of(tenantA, tenantB)), () -> group(groupTenantId).getId());
    TenantContext.clearCurrentTenant();
    inOwnTransaction(
        "SET LOCAL session_replication_role = replica",
        writer ->
            writer.update(
                "INSERT INTO groups_roles (group_id, role_id) VALUES (?, ?)",
                groupId,
                hiddenRoleId));
    return groupId;
  }

  /**
   * Runs the writes by JDBC in one committed transaction of their own, opened by {@code setting}. A
   * write that fails rolls the transaction back and rethrows.
   */
  private void inOwnTransaction(String setting, Consumer<JdbcTemplate> writes) {
    try (Connection connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
      connection.setAutoCommit(false);
      try {
        JdbcTemplate writer = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        writer.execute(setting);
        writes.accept(writer);
        connection.commit();
      } catch (RuntimeException e) {
        connection.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Could not run the JDBC writes", e);
    }
  }

  /** A committed group of the tenant (a platform group when null) holding a role of its own. */
  private String committedGroup(String tenantId, Capability capability) {
    String groupId =
        tenantTx.executeAsPlatformWrite(
            TxCtx.forTenants(List.of(tenantA, tenantB)),
            () -> group(tenantId, role(tenantId, capability)).getId());
    TenantContext.clearCurrentTenant();
    return groupId;
  }

  private String committedRole(String tenantId, Capability capability) {
    String roleId =
        tenantTx.executeAsPlatformWrite(
            TxCtx.forTenants(List.of(tenantA, tenantB)), () -> role(tenantId, capability).getId());
    TenantContext.clearCurrentTenant();
    return roleId;
  }

  private void membership(User user, String tenantId) {
    tenantTx.execute(
        TxCtx.forTenant(tenantId), () -> tenantRepository.addUserToTenant(user.getId(), tenantId));
    membershipCache.evict(user.getId(), tenantId);
  }

  private static Authentication authenticated(User user) {
    return buildAuthenticationToken(user);
  }

  private boolean defaultAssignOf(String groupId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT group_default_user_assign FROM groups WHERE group_id = ?",
            Boolean.class,
            groupId));
  }

  private int usersOf(String groupId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM users_groups WHERE group_id = ?", Integer.class, groupId);
  }

  private List<String> rolesOf(String groupId) {
    return jdbc.queryForList(
        "SELECT role_id FROM groups_roles WHERE group_id = ?", String.class, groupId);
  }

  private String tenantInformationBody() {
    return asJsonString(Map.of("group_name", "renamed", "group_default_user_assign", true));
  }

  private String tenantUsersBody() {
    return asJsonString(Map.of("group_users", List.of(member.getId())));
  }

  @Nested
  @DisplayName("a group of tenant B linked to a role of tenant A")
  class TenantGroup {

    @Test
    @DisplayName("given_aHiddenRole_should_refuseToMakeTheGroupDefault")
    void given_aHiddenRole_should_refuseToMakeTheGroupDefault() throws Exception {
      // Arrange
      String groupId = groupWithHiddenRole(tenantB);

      // Act
      mvc.perform(
              put(TENANT_GROUP_URI + "/{groupId}/information", tenantB, groupId)
                  .with(authentication(authenticated(tenantCaller)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(tenantInformationBody()))
          // Assert
          .andExpect(status().isForbidden());
      assertThat(defaultAssignOf(groupId)).isFalse();
    }

    @Test
    @DisplayName("given_aHiddenRole_should_refuseToChangeTheUsersOfTheGroup")
    void given_aHiddenRole_should_refuseToChangeTheUsersOfTheGroup() throws Exception {
      // Arrange
      String groupId = groupWithHiddenRole(tenantB);

      // Act
      mvc.perform(
              put(TENANT_GROUP_URI + "/{groupId}/users", tenantB, groupId)
                  .with(authentication(authenticated(tenantCaller)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(tenantUsersBody()))
          // Assert
          .andExpect(status().isForbidden());
      assertThat(usersOf(groupId)).isZero();
    }

    @Test
    @DisplayName("given_aHiddenRoleAndAPlatformAdmin_should_refuseToMakeTheGroupDefault")
    void given_aHiddenRoleAndAPlatformAdmin_should_refuseToMakeTheGroupDefault() throws Exception {
      // Arrange
      String groupId = groupWithHiddenRole(tenantB);

      // Act
      mvc.perform(
              put(TENANT_GROUP_URI + "/{groupId}/information", tenantB, groupId)
                  .with(authentication(authenticated(tenantAdmin)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(tenantInformationBody()))
          // Assert
          .andExpect(status().isForbidden());
      assertThat(defaultAssignOf(groupId)).isFalse();
    }

    @Test
    @DisplayName("given_aHiddenRoleAndABypassHolder_should_refuseToChangeTheUsersOfTheGroup")
    void given_aHiddenRoleAndABypassHolder_should_refuseToChangeTheUsersOfTheGroup()
        throws Exception {
      // Arrange
      String groupId = groupWithHiddenRole(tenantB);

      // Act
      mvc.perform(
              put(TENANT_GROUP_URI + "/{groupId}/users", tenantB, groupId)
                  .with(authentication(authenticated(tenantBypass)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(tenantUsersBody()))
          // Assert
          .andExpect(status().isForbidden());
      assertThat(usersOf(groupId)).isZero();
    }
  }

  @Nested
  @DisplayName("a group of tenant B whose roles all read")
  class ReadableRoles {

    @Test
    @DisplayName("given_aRoleTheCallerHolds_should_makeTheGroupDefault")
    void given_aRoleTheCallerHolds_should_makeTheGroupDefault() throws Exception {
      // Arrange
      String groupId = committedGroup(tenantB, Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES);

      // Act
      mvc.perform(
              put(TENANT_GROUP_URI + "/{groupId}/information", tenantB, groupId)
                  .with(authentication(authenticated(tenantCaller)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(tenantInformationBody()))
          // Assert
          .andExpect(status().isOk());
      assertThat(defaultAssignOf(groupId)).isTrue();
    }

    @Test
    @DisplayName("given_aRoleTheCallerLacks_should_refuseToChangeTheUsersOfTheGroup")
    void given_aRoleTheCallerLacks_should_refuseToChangeTheUsersOfTheGroup() throws Exception {
      // Arrange
      String groupId = committedGroup(tenantB, HIDDEN_CAPABILITY);

      // Act
      mvc.perform(
              put(TENANT_GROUP_URI + "/{groupId}/users", tenantB, groupId)
                  .with(authentication(authenticated(tenantCaller)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(tenantUsersBody()))
          // Assert
          .andExpect(status().isBadRequest());
      assertThat(usersOf(groupId)).isZero();
    }
  }

  @Nested
  @DisplayName("a platform group linked to a role of tenant A")
  class PlatformGroup {

    @Test
    @DisplayName("given_aHiddenRole_should_refuseToMakeTheGroupDefault")
    void given_aHiddenRole_should_refuseToMakeTheGroupDefault() throws Exception {
      // Arrange
      String groupId = groupWithHiddenRole(null);

      // Act
      mvc.perform(
              put(PLATFORM_GROUPS_URI + "/{groupId}", groupId)
                  .with(authentication(authenticated(platformCaller)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new PlatformGroupInput("renamed", null, true))))
          // Assert
          .andExpect(status().isForbidden());
      assertThat(defaultAssignOf(groupId)).isFalse();
    }

    @Test
    @DisplayName("given_aHiddenRole_should_refuseToChangeTheUsersOfTheGroup")
    void given_aHiddenRole_should_refuseToChangeTheUsersOfTheGroup() throws Exception {
      // Arrange
      String groupId = groupWithHiddenRole(null);

      // Act
      mvc.perform(
              put(PLATFORM_GROUPS_URI + "/{groupId}/users", groupId)
                  .with(authentication(authenticated(platformCaller)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(new PlatformGroupUpdateUsersInput(List.of(member.getId())))))
          // Assert
          .andExpect(status().isForbidden());
      assertThat(usersOf(groupId)).isZero();
    }

    @Test
    @DisplayName("given_aHiddenRoleAndAPlatformAdmin_should_refuseToChangeTheUsersOfTheGroup")
    void given_aHiddenRoleAndAPlatformAdmin_should_refuseToChangeTheUsersOfTheGroup()
        throws Exception {
      // Arrange
      String groupId = groupWithHiddenRole(null);

      // Act
      mvc.perform(
              put(PLATFORM_GROUPS_URI + "/{groupId}/users", groupId)
                  .with(authentication(authenticated(platformAdmin)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(new PlatformGroupUpdateUsersInput(List.of(member.getId())))))
          // Assert
          .andExpect(status().isForbidden());
      assertThat(usersOf(groupId)).isZero();
    }

    @Test
    @DisplayName("given_aHiddenRoleAndABypassHolder_should_refuseToMakeTheGroupDefault")
    void given_aHiddenRoleAndABypassHolder_should_refuseToMakeTheGroupDefault() throws Exception {
      // Arrange
      String groupId = groupWithHiddenRole(null);

      // Act
      mvc.perform(
              put(PLATFORM_GROUPS_URI + "/{groupId}", groupId)
                  .with(authentication(authenticated(platformBypass)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new PlatformGroupInput("renamed", null, true))))
          // Assert
          .andExpect(status().isForbidden());
      assertThat(defaultAssignOf(groupId)).isFalse();
    }
  }

  @Nested
  @DisplayName("a platform group whose roles all read")
  class ReadablePlatformRoles {

    @Test
    @DisplayName("given_aPlatformRoleTheCallerHolds_should_makeTheGroupDefault")
    void given_aPlatformRoleTheCallerHolds_should_makeTheGroupDefault() throws Exception {
      // Arrange
      String groupId = committedGroup(null, Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES);

      // Act
      mvc.perform(
              put(PLATFORM_GROUPS_URI + "/{groupId}", groupId)
                  .with(authentication(authenticated(platformCaller)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new PlatformGroupInput("renamed", null, true))))
          // Assert
          .andExpect(status().isOk());
      assertThat(defaultAssignOf(groupId)).isTrue();
    }
  }

  @Nested
  @DisplayName("a role of a tenant sent to a platform group")
  class PlatformRoles {

    @Test
    @DisplayName("given_aTenantTheCallerBelongsTo_should_answerNotFound")
    void given_aTenantTheCallerBelongsTo_should_answerNotFound() throws Exception {
      // Arrange
      String groupId = committedGroup(null, Capability.ACCESS_CHANNELS);
      String tenantRoleId = committedRole(tenantA, Capability.ACCESS_CHANNELS);

      // Act
      mvc.perform(
              put(PLATFORM_GROUPS_URI + "/{groupId}/platform-roles", groupId)
                  .with(authentication(authenticated(platformCallerMemberOfA)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new PlatformGroupUpdateRolesInput(List.of(tenantRoleId)))))
          // Assert
          .andExpect(status().isNotFound());
      assertThat(rolesOf(groupId)).hasSize(1);
    }
  }
}
