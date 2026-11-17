package io.openaev.rest.user;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
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
import io.openaev.rest.user.form.login.LoginUserInput;
import io.openaev.service.UserService;
import io.openaev.service.user_events.UserEventService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The response of {@code /api/login} installs the identity of the frontend: the capabilities and
 * the flags derived from the roles of the user's groups are the ones it holds in the ambient
 * tenant, the default one on this route. The endpoint opens its transaction with no tenant scope,
 * and with {@code roles} among the active tables a load reads none of the tenant roles there.
 *
 * <p>NOT {@code @Transactional}: the data is committed, and the login opens its own transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Login response with roles active")
class UserApiLoginAmbientRolesTest extends IntegrationTest {

  /** A capability held only through a role of the default tenant. */
  private static final Capability DEFAULT_TENANT_CAPABILITY = Capability.ACCESS_ASSESSMENT;

  /** A capability held only through a role of tenant A. */
  private static final Capability TENANT_A_CAPABILITY = Capability.ACCESS_CHANNELS;

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables",
        () ->
            String.join(
                ",",
                Stream.concat(ProductionActiveTables.names().stream(), Stream.of("roles"))
                    .distinct()
                    .toList()));
  }

  @MockitoBean private UserEventService userEventService;

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
  private User member;
  private final List<String> roleIds = new java.util.ArrayList<>();
  private final List<String> groupIds = new java.util.ArrayList<>();

  @BeforeAll
  void seed() throws Exception {
    userService.createAdminSession();
    try {
      tenantA = tenantHelper.createTenant("login-roles-a").getId();
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clearCurrentTenant();
    }
    member =
        userRepository.save(
            UserFixture.getUser(
                "login-roles",
                "login-roles",
                "login-roles-" + UUID.randomUUID() + "@unittests.invalid",
                false));
    member.setPassword(UserFixture.ENCODED_PASSWORD);
    member = userRepository.save(member);
    tenantTx.executeAsPlatformWrite(
        TxCtx.forTenants(List.of(Tenant.DEFAULT_TENANT_UUID, tenantA)),
        () -> {
          member
              .getUnscopedGroups()
              .add(tenantGroup(Tenant.DEFAULT_TENANT_UUID, DEFAULT_TENANT_CAPABILITY));
          member.getUnscopedGroups().add(tenantGroup(tenantA, TENANT_A_CAPABILITY));
          userRepository.save(member);
        });
    for (String tenantId : List.of(Tenant.DEFAULT_TENANT_UUID, tenantA)) {
      tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () -> tenantRepository.addUserToTenant(member.getId(), tenantId));
      membershipCache.evict(member.getId(), tenantId);
    }
    TenantContext.clearCurrentTenant();
  }

  @AfterAll
  void cleanup() {
    jdbc.update("DELETE FROM users_groups WHERE user_id = ?", member.getId());
    for (String groupId : groupIds) {
      jdbc.update("DELETE FROM groups_roles WHERE group_id = ?", groupId);
      jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
    }
    for (String roleId : roleIds) {
      jdbc.update("DELETE FROM roles_capabilities WHERE role_id = ?", roleId);
      jdbc.update("DELETE FROM roles WHERE role_id = ?", roleId);
    }
    jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM users WHERE user_id = ?", member.getId());
    tenantHelper.deleteCommittedTenants(tenantA);
    TenantContext.clearCurrentTenant();
  }

  private Group tenantGroup(String tenantId, Capability capability) {
    Role role = new Role();
    role.setName("role-" + UUID.randomUUID());
    role.setCapabilities(Set.of(capability));
    role.setTenant(new Tenant(tenantId));
    Role savedRole = roleRepository.save(role);
    roleIds.add(savedRole.getId());
    Group group = new Group();
    group.setName("group-" + UUID.randomUUID());
    group.setTenant(new Tenant(tenantId));
    group.getRoles().add(savedRole);
    Group savedGroup = groupRepository.save(group);
    groupIds.add(savedGroup.getId());
    return savedGroup;
  }

  @Nested
  @DisplayName("a member of the default tenant and of tenant A, holding a role of each")
  class MemberOfTwoTenants {

    @Test
    @DisplayName("given_validCredentials_should_sendTheCapabilitiesOfTheDefaultTenant")
    void given_validCredentials_should_sendTheCapabilitiesOfTheDefaultTenant() throws Exception {
      // Arrange
      LoginUserInput input =
          LoginUserInput.builder()
              .login(member.getEmail())
              .password(UserFixture.RAW_PASSWORD)
              .build();

      // Act + Assert
      mvc.perform(
              post("/api/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.user_capabilities[?(@ == '" + DEFAULT_TENANT_CAPABILITY + "')]").exists())
          .andExpect(
              jsonPath("$.user_capabilities[?(@ == '" + TENANT_A_CAPABILITY + "')]")
                  .doesNotExist());
    }
  }
}
