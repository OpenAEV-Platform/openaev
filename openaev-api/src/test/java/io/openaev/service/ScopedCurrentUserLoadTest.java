package io.openaev.service;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.aop.AccessControl;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.channel.ChannelApi;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FlushModeType;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current user is loaded with the roles of its groups, with {@code roles} among the active
 * tables. The roles of a group are an eager association of the user, read when the user is loaded;
 * a tenant role is a row of an active table, so a load made with no tenant scope reads none of them
 * and the user holds no tenant capability.
 *
 * <p>NOT {@code @Transactional}: a test transaction runs in the default tenant's scope, which gives
 * every load the scope this class proves the application has to give it itself. The users are
 * authenticated the way {@code TokenAuthenticationFilter} does it, and the data is committed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ScopedCurrentUserLoadTest.CapabilityProbe.class)
@DisplayName("Current user load with roles active")
class ScopedCurrentUserLoadTest extends IntegrationTest {

  private static final String PROBE_PATH = "/test-roles-scoped-load";
  private static final String GUARDED_URI = "/api" + PROBE_PATH + "/guarded";
  private static final String TENANT_GUARDED_URI = TENANT_PREFIX + PROBE_PATH + "/guarded";
  private static final String TENANT_HANDLER_CAPABILITIES_URI =
      TENANT_PREFIX + PROBE_PATH + "/handler-capabilities";
  private static final String TENANT_LOADERS_URI = TENANT_PREFIX + PROBE_PATH + "/loaders";

  /** What the guarded endpoints check: the capability of {@code CHANNEL} {@code SEARCH}. */
  private static final Capability GUARD_CAPABILITY = Capability.ACCESS_CHANNELS;

  /** A capability no guard checks, held through a tenant role only. */
  private static final Capability TENANT_ONLY_CAPABILITY = Capability.ACCESS_ASSESSMENT;

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

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantMembershipCacheManager membershipCache;
  @Autowired private CacheManager cacheManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;
  @Autowired private EntityManagerFactory emf;
  @Autowired private StreamService streamService;

  private String tenantA;
  private String tenantB;
  private User memberOfB;
  private User memberOfAAndB;
  private User platformRoleHolder;
  private User tenantAndPlatformRoleHolder;
  private String bearerToken;
  private final List<String> platformRoleIds = new ArrayList<>();
  private final List<String> platformGroupIds = new ArrayList<>();
  private final List<String> userIds = new ArrayList<>();

  @BeforeAll
  void seed() throws Exception {
    userService.createAdminSession();
    try {
      tenantA = tenantHelper.createTenant("roles-load-a").getId();
      tenantB = tenantHelper.createTenant("roles-load-b").getId();
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clearCurrentTenant();
    }

    memberOfB = newUser("member-of-b");
    memberOfAAndB = newUser("member-of-a-and-b");
    platformRoleHolder = newUser("platform-role-holder");
    tenantAndPlatformRoleHolder = newUser("tenant-and-platform-role-holder");

    tenantTx.executeAsPlatformWrite(
        TxCtx.forTenants(List.of(tenantA, tenantB)),
        () -> {
          memberOfB.getUnscopedGroups().add(tenantGroup(tenantB, Set.of(GUARD_CAPABILITY)));
          memberOfAAndB.getUnscopedGroups().add(tenantGroup(tenantB, Set.of(GUARD_CAPABILITY)));
          platformRoleHolder.getUnscopedGroups().add(platformGroup(Set.of(GUARD_CAPABILITY)));
          tenantAndPlatformRoleHolder
              .getUnscopedGroups()
              .add(tenantGroup(tenantB, Set.of(TENANT_ONLY_CAPABILITY)));
          tenantAndPlatformRoleHolder
              .getUnscopedGroups()
              .add(platformGroup(Set.of(GUARD_CAPABILITY)));
          userRepository.saveAll(
              List.of(memberOfB, memberOfAAndB, platformRoleHolder, tenantAndPlatformRoleHolder));
        });

    member(memberOfB, tenantB);
    member(memberOfAAndB, tenantA);
    member(memberOfAAndB, tenantB);
    member(tenantAndPlatformRoleHolder, tenantB);
    bearerToken = UUID.randomUUID().toString();
    userService.createUserToken(memberOfB, bearerToken);
    TenantContext.clearCurrentTenant();
  }

  @AfterAll
  void cleanup() {
    String[] users = userIds.toArray(String[]::new);
    for (String userId : users) {
      jdbc.update("DELETE FROM tokens WHERE token_user = ?", userId);
      jdbc.update("DELETE FROM users_groups WHERE user_id = ?", userId);
    }
    for (String groupId : platformGroupIds) {
      jdbc.update("DELETE FROM groups_roles WHERE group_id = ?", groupId);
      jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
    }
    deletePlatformRoles();
    for (String userId : users) {
      jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  /**
   * Deletes the platform roles and their capabilities in one committed transaction that sets {@code
   * app.platform_write} first, the only kind of transaction in which the database guard of platform
   * roles accepts the write.
   */
  private void deletePlatformRoles() {
    try (Connection connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
      connection.setAutoCommit(false);
      try {
        JdbcTemplate writer = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        writer.queryForObject(
            "SELECT set_config('app.platform_write', 'true', true)", String.class);
        for (String roleId : platformRoleIds) {
          writer.update("DELETE FROM roles_capabilities WHERE role_id = ?", roleId);
          writer.update("DELETE FROM roles WHERE role_id = ?", roleId);
        }
        connection.commit();
      } catch (RuntimeException e) {
        connection.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Could not delete the platform roles", e);
    }
  }

  private User newUser(String label) {
    User user =
        userRepository.save(
            UserFixture.getUser(
                label, label, label + "-" + UUID.randomUUID() + "@unittests.invalid", false));
    userIds.add(user.getId());
    return user;
  }

  private Group tenantGroup(String tenantId, Set<Capability> capabilities) {
    Role role = new Role();
    role.setName("role-" + UUID.randomUUID());
    role.setCapabilities(capabilities);
    role.setTenant(new Tenant(tenantId));
    Role savedRole = roleRepository.save(role);
    Group group = new Group();
    group.setName("group-" + UUID.randomUUID());
    group.setTenant(new Tenant(tenantId));
    group.getRoles().add(savedRole);
    return groupRepository.save(group);
  }

  private Group platformGroup(Set<Capability> capabilities) {
    Role role = new Role();
    role.setName("platform-role-" + UUID.randomUUID());
    role.setCapabilities(capabilities);
    Role savedRole = roleRepository.save(role);
    platformRoleIds.add(savedRole.getId());
    Group group = new Group();
    group.setName("platform-group-" + UUID.randomUUID());
    group.getRoles().add(savedRole);
    Group savedGroup = groupRepository.save(group);
    platformGroupIds.add(savedGroup.getId());
    return savedGroup;
  }

  private void member(User user, String tenantId) {
    tenantTx.execute(
        TxCtx.forTenant(tenantId), () -> tenantRepository.addUserToTenant(user.getId(), tenantId));
    membershipCache.evict(user.getId(), tenantId);
  }

  private static Authentication authenticated(User user) {
    return buildAuthenticationToken(user);
  }

  private String firstnameOf(User user) {
    return jdbc.queryForObject(
        "SELECT user_firstname FROM users WHERE user_id = ?", String.class, user.getId());
  }

  private void evictAdminCache() {
    Cache adminUsers = cacheManager.getCache("adminUsers");
    if (adminUsers != null) {
      adminUsers.clear();
    }
  }

  @Nested
  @DisplayName("a user member of one tenant, holding a role of that tenant")
  class MemberOfOneTenant {

    @Test
    @DisplayName("given_tenantRoleOfTheRoute_should_passTheCapabilityCheck")
    void given_tenantRoleOfTheRoute_should_passTheCapabilityCheck() throws Exception {
      // Arrange
      Authentication caller = authenticated(memberOfB);

      // Act + Assert
      mvc.perform(get(TENANT_GUARDED_URI, tenantB).with(authentication(caller)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("given_tenantRoleOfTheRoute_should_passTheCapabilityCheckOfARealEndpoint")
    void given_tenantRoleOfTheRoute_should_passTheCapabilityCheckOfARealEndpoint()
        throws Exception {
      // Arrange
      Authentication caller = authenticated(memberOfB);

      // Act + Assert
      mvc.perform(get(ChannelApi.TENANT_CHANNEL_URI, tenantB).with(authentication(caller)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("given_aBearerToken_should_passTheCapabilityCheck")
    void given_aBearerToken_should_passTheCapabilityCheck() throws Exception {
      // Act + Assert
      mvc.perform(get(TENANT_GUARDED_URI, tenantB).header("Authorization", "Bearer " + bearerToken))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("a user member of two tenants, holding a role of one of them")
  class MemberOfTwoTenants {

    @Test
    @DisplayName("given_theRoleOfTheOtherTenant_should_refuseOnTheRouteOfTheFirst")
    void given_theRoleOfTheOtherTenant_should_refuseOnTheRouteOfTheFirst() throws Exception {
      // Arrange
      Authentication caller = authenticated(memberOfAAndB);

      // Act + Assert
      mvc.perform(get(TENANT_GUARDED_URI, tenantA).with(authentication(caller)))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given_theRoleOfTheOtherTenant_should_passOnTheRouteOfTheTenantHoldingIt")
    void given_theRoleOfTheOtherTenant_should_passOnTheRouteOfTheTenantHoldingIt()
        throws Exception {
      // Arrange
      Authentication caller = authenticated(memberOfAAndB);

      // Act + Assert
      mvc.perform(get(TENANT_GUARDED_URI, tenantB).with(authentication(caller)))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("a user with no membership, holding a platform role")
  class NoMembership {

    @Test
    @DisplayName("given_aPlatformRole_should_passTheCapabilityCheck")
    void given_aPlatformRole_should_passTheCapabilityCheck() throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();
      Authentication caller = authenticated(platformRoleHolder);

      // Act + Assert
      mvc.perform(get(GUARDED_URI).with(authentication(caller))).andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("capabilities read by the handler once the loader returned")
  class AfterTheLoader {

    @Test
    @DisplayName("given_aTenantRoleAndAPlatformRole_should_exposeTheTenantCapabilityToTheHandler")
    void given_aTenantRoleAndAPlatformRole_should_exposeTheTenantCapabilityToTheHandler()
        throws Exception {
      // Arrange
      Authentication caller = authenticated(tenantAndPlatformRoleHolder);

      // Act + Assert
      mvc.perform(get(TENANT_HANDLER_CAPABILITIES_URI, tenantB).with(authentication(caller)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@ == '" + TENANT_ONLY_CAPABILITY + "')]").exists());
    }

    @Test
    @DisplayName("given_currentUserOrNullFirst_should_returnTheCapabilitiesOfCurrentUser")
    void given_currentUserOrNullFirst_should_returnTheCapabilitiesOfCurrentUser() throws Exception {
      // Arrange
      Authentication caller = authenticated(tenantAndPlatformRoleHolder);

      // Act + Assert
      mvc.perform(get(TENANT_LOADERS_URI, tenantB).with(authentication(caller)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.or_null[?(@ == '" + TENANT_ONLY_CAPABILITY + "')]").exists())
          .andExpect(jsonPath("$.current[?(@ == '" + TENANT_ONLY_CAPABILITY + "')]").exists());
    }
  }

  @Nested
  @DisplayName("the platform admin")
  class PlatformAdmin {

    @Test
    @DisplayName("given_aColdAdminCache_should_passTheCapabilityCheck")
    void given_aColdAdminCache_should_passTheCapabilityCheck() throws Exception {
      // Arrange
      evictAdminCache();
      Authentication admin = authenticated(admin());

      // Act + Assert
      mvc.perform(get(GUARDED_URI).with(authentication(admin))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("given_aWarmAdminCache_should_passTheCapabilityCheck")
    void given_aWarmAdminCache_should_passTheCapabilityCheck() throws Exception {
      // Arrange
      evictAdminCache();
      Authentication admin = authenticated(admin());
      mvc.perform(get(GUARDED_URI).with(authentication(admin))).andExpect(status().isOk());

      // Act + Assert
      mvc.perform(get(GUARDED_URI).with(authentication(admin))).andExpect(status().isOk());
    }

    private User admin() {
      return userRepository.findById(User.ADMIN_UUID).orElseThrow();
    }
  }

  @Nested
  @DisplayName("a load made outside any transaction, as the stream and the async work make it")
  class OutsideATransaction {

    @Test
    @DisplayName("given_aTenantRoleOfTheOnlyMembership_should_loadItsCapabilities")
    void given_aTenantRoleOfTheOnlyMembership_should_loadItsCapabilities() {
      // Act
      Set<Capability> capabilities = asUser(memberOfB, tenantB, userService::currentUser);

      // Assert
      assertThat(capabilities).contains(GUARD_CAPABILITY);
    }

    @Test
    @DisplayName("given_aTenantRoleOfTheOtherMembership_should_loadItsCapabilitiesInThatTenant")
    void given_aTenantRoleOfTheOtherMembership_should_loadItsCapabilitiesInThatTenant() {
      // Act
      Set<Capability> inB = asUser(memberOfAAndB, tenantB, userService::currentUser);
      Set<Capability> inA = asUser(memberOfAAndB, tenantA, userService::currentUser);

      // Assert
      assertThat(inB).contains(GUARD_CAPABILITY);
      assertThat(inA).doesNotContain(GUARD_CAPABILITY);
    }

    @Test
    @DisplayName("given_aUserWithNoMembership_should_loadTheCapabilitiesOfItsPlatformRole")
    void given_aUserWithNoMembership_should_loadTheCapabilitiesOfItsPlatformRole() {
      // Act
      Set<Capability> capabilities = asUser(platformRoleHolder, tenantA, userService::currentUser);

      // Assert
      assertThat(capabilities).contains(GUARD_CAPABILITY);
    }

    @Test
    @DisplayName("given_currentUserOrNull_should_loadTheCapabilitiesOfCurrentUser")
    void given_currentUserOrNull_should_loadTheCapabilitiesOfCurrentUser() {
      // Act
      Set<Capability> capabilities = asUser(memberOfB, tenantB, userService::currentUserOrNull);

      // Assert
      assertThat(capabilities).contains(GUARD_CAPABILITY);
    }

    @Test
    @DisplayName("given_theStreamResolvesAConsumer_should_loadTheCapabilitiesOfItsTenantRole")
    void given_theStreamResolvesAConsumer_should_loadTheCapabilitiesOfItsTenantRole() {
      // Act
      Set<Capability> capabilities =
          asUser(
              memberOfB,
              tenantB,
              () ->
                  ReflectionTestUtils.invokeMethod(
                      streamService, "resolveUser", memberOfB.getId()));

      // Assert
      assertThat(capabilities).contains(GUARD_CAPABILITY);
    }

    @Test
    @DisplayName("given_aDirtyEntityInTheSession_should_flushNothingWhenLoadingTheUser")
    void given_aDirtyEntityInTheSession_should_flushNothingWhenLoadingTheUser() {
      // Arrange: a session bound to the thread, as open-in-view leaves it, with a managed entity
      // changed and not yet written
      String untouched = "untouched-" + UUID.randomUUID();
      jdbc.update(
          "UPDATE users SET user_firstname = ? WHERE user_id = ?",
          untouched,
          platformRoleHolder.getId());
      EntityManager session = emf.createEntityManager();
      TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(session));
      try {
        User dirty = session.find(User.class, platformRoleHolder.getId());
        dirty.setFirstname("flushed-by-the-load");

        // Act
        asUser(memberOfB, tenantB, userService::currentUser);

        // Assert
        assertThat(firstnameOf(platformRoleHolder)).isEqualTo(untouched);
      } finally {
        TransactionSynchronizationManager.unbindResource(emf);
        session.close();
      }
    }
  }

  @Nested
  @DisplayName("a load made inside a transaction a job opened")
  class InsideAScopedTransaction {

    @Test
    @DisplayName("given_aTransactionScopedToOneTenant_should_loadWithoutChangingItsScope")
    void given_aTransactionScopedToOneTenant_should_loadWithoutChangingItsScope() {
      // Arrange
      SecurityContextHolder.getContext().setAuthentication(authenticated(memberOfAAndB));
      try {
        // Act
        String scopeAfter =
            tenantTx.execute(
                TxCtx.forTenant(tenantA),
                () -> {
                  userService.currentUser();
                  return (String)
                      em.createNativeQuery(
                              "SELECT coalesce(current_setting('app.current_tenants', true), '')")
                          .setFlushMode(FlushModeType.COMMIT)
                          .getSingleResult();
                });

        // Assert
        assertThat(scopeAfter).isEqualTo(tenantA);
      } finally {
        SecurityContextHolder.clearContext();
      }
    }

    @Test
    @DisplayName("given_aRoleOfTheOtherTenant_should_loadNoneOfItsRolesUnderTheScopeOfThisOne")
    void given_aRoleOfTheOtherTenant_should_loadNoneOfItsRolesUnderTheScopeOfThisOne() {
      // Arrange
      SecurityContextHolder.getContext().setAuthentication(authenticated(memberOfAAndB));
      try {
        // Act: the roles are read inside the transaction, scoped to A
        LoadedRoles loaded =
            tenantTx.execute(
                TxCtx.forTenant(tenantA),
                () -> {
                  User user = userService.currentUser();
                  return new LoadedRoles(
                      user.getUnscopedGroups().stream()
                          .flatMap(group -> group.getRoles().stream())
                          .map(Role::getId)
                          .toList(),
                      user.getCapabilities());
                });

        // Assert: no row of B's role comes back, and with it no capability
        assertThat(loaded.roleIds()).isEmpty();
        assertThat(loaded.capabilities()).doesNotContain(GUARD_CAPABILITY);
      } finally {
        SecurityContextHolder.clearContext();
      }
    }

    @Test
    @DisplayName("given_aReadWriteTransaction_should_loadWithoutMakingItReadOnly")
    void given_aReadWriteTransaction_should_loadWithoutMakingItReadOnly() {
      // Arrange
      SecurityContextHolder.getContext().setAuthentication(authenticated(memberOfAAndB));
      try {
        // Act
        List<Boolean> readOnly =
            tenantTx.execute(
                TxCtx.forTenant(tenantA),
                () -> {
                  boolean before = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                  userService.currentUser();
                  return List.of(
                      before, TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                });

        // Assert
        assertThat(readOnly).containsExactly(false, false);
      } finally {
        SecurityContextHolder.clearContext();
      }
    }

    @Test
    @DisplayName("given_aReadOnlyTransaction_should_loadWithoutMakingItReadWrite")
    void given_aReadOnlyTransaction_should_loadWithoutMakingItReadWrite() {
      // Arrange
      SecurityContextHolder.getContext().setAuthentication(authenticated(memberOfAAndB));
      try {
        // Act
        List<Boolean> readOnly =
            tenantTx.executeReadOnly(
                TxCtx.forTenant(tenantA),
                () -> {
                  boolean before = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                  userService.currentUser();
                  return List.of(
                      before, TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                });

        // Assert
        assertThat(readOnly).containsExactly(true, true);
      } finally {
        SecurityContextHolder.clearContext();
      }
    }

    private record LoadedRoles(List<String> roleIds, Set<Capability> capabilities) {}
  }

  /**
   * Runs {@code loader} as {@code user} with {@code ambientTenant} as the request's tenant, the way
   * the interceptor and the authentication filter leave the thread for a handler, and returns the
   * capabilities of the user it loaded, read once the loader has returned.
   */
  private Set<Capability> asUser(User user, String ambientTenant, Supplier<User> loader) {
    SecurityContextHolder.getContext().setAuthentication(authenticated(user));
    TenantContext.setCurrentTenant(ambientTenant);
    try {
      return loader.get().getCapabilities();
    } finally {
      TenantContext.clearCurrentTenant();
      SecurityContextHolder.clearContext();
    }
  }

  /** Endpoints of the test: the shape of a production endpoint, a transaction and a scope. */
  @RestController
  @RequiredArgsConstructor
  public static class CapabilityProbe {

    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping({GUARDED_URI, TENANT_GUARDED_URI})
    @Transactional
    @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.CHANNEL)
    public String guarded(TxCtx ctx) {
      return "ok";
    }

    @GetMapping(TENANT_HANDLER_CAPABILITIES_URI)
    @Transactional
    @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.CHANNEL)
    public Set<Capability> handlerCapabilities(TxCtx ctx) {
      return userRepository
          .findById(userService.currentUser().getId())
          .orElseThrow()
          .getCapabilities();
    }

    @GetMapping(TENANT_LOADERS_URI)
    @Transactional
    @AccessControl(skipRBAC = true)
    public Map<String, Set<Capability>> loaders(TxCtx ctx) {
      Set<Capability> orNull = userService.currentUserOrNull().getCapabilities();
      Set<Capability> current = userService.currentUser().getCapabilities();
      return Map.of("or_null", orNull, "current", current);
    }
  }
}
