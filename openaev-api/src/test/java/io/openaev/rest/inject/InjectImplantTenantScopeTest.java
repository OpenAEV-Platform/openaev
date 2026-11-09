package io.openaev.rest.inject;

import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.rest.inject.InjectApi.TENANT_INJECT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.aop.lock.Lock;
import io.openaev.aop.lock.LockResourceType;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.rest.exception.ForbiddenException;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.service.InjectExecutionCallbackService;
import io.openaev.service.queue.BatchQueueService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.KeepRabbit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two implant endpoints that take an inject id (the execution callback of an agent and the
 * executable payload) load the inject in the request's tenant scope, besides checking that the
 * agent targets it.
 *
 * <p>The platform hands the implant the token of the inject's own tenant: a service account that
 * belongs to that tenant only, so the scope of its requests is that tenant on the prefixed route
 * and on the non-prefixed one alike. Every request below carries such a caller: the {@code
 * AGENT_RUNTIME_ACCESS} capability and one membership, never the admin shortcut.
 *
 * <p>The tenant tests are not {@code @Transactional}: the rows are committed in their own tenant
 * and read back with plain SQL, the way production sees them.
 */
@KeepRabbit
@Import(InjectImplantTenantScopeTest.LockHolderConfig.class)
@DisplayName("Implant inject endpoints load the inject in the request's tenant scope")
class InjectImplantTenantScopeTest extends IntegrationTest {

  private enum Endpoint {
    CALLBACK,
    EXECUTABLE_PAYLOAD
  }

  private enum Route {
    /** {@code /api/injects/...}: no path tenant, no selector, the scope is the memberships. */
    NON_PREFIXED,
    /** {@code /api/injects/...} with {@code X-Tenant-Ids}: the selector narrows the memberships. */
    SELECTOR_HEADER,
    /** {@code /api/tenants/{tenantId}/injects/...}: the path names the tenant. */
    PREFIXED
  }

  private static final String UNPREFIXED_CALLS =
      "io.openaev.rest.inject.InjectImplantTenantScopeTest#unprefixedCalls";

  static Stream<Arguments> unprefixedCalls() {
    return calls().filter(arguments -> arguments.get()[1] != Route.PREFIXED);
  }

  private static final String CALLS = "io.openaev.rest.inject.InjectImplantTenantScopeTest#calls";

  static Stream<Arguments> calls() {
    return Stream.of(Endpoint.values())
        .flatMap(endpoint -> Stream.of(Route.values()).map(route -> Arguments.of(endpoint, route)));
  }

  private record Seeded(String agentId, String injectId, String endpointId) {}

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private LockHolder lockHolder;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectExecutionCallbackService injectExecutionCallbackService;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private PayloadComposer payloadComposer;

  private BatchQueueService<InjectExecutionCallback> savedQueueService;

  @BeforeEach
  void useTheSynchronousIngestion() {
    // No queue: the callback is ingested inside the request, so its execution traces can be read
    // back from the database when the response returns.
    savedQueueService = injectExecutionCallbackService.getInjectTraceQueueService();
    injectExecutionCallbackService.setInjectTraceQueueService(null);
  }

  @AfterEach
  void restoreTheQueue() {
    injectExecutionCallbackService.setInjectTraceQueueService(savedQueueService);
    TenantContext.clearCurrentTenant();
  }

  /**
   * The agent of a non-default tenant calls with its tenant's service-account token: the {@code
   * AGENT_RUNTIME_ACCESS} capability in that tenant, and no other membership. The capability is
   * checked in the ambient tenant, which only the prefixed route sets, so that is the route it
   * calls (the command line the platform builds carries {@code --tenant-id}).
   */
  @Nested
  @WithMockUser
  @DisplayName("An agent of a non-default tenant, on the prefixed route")
  class NonDefaultTenant {

    /** The tenant of the agent's service account: the caller's only membership. */
    private String memberTenant;

    /** A tenant the caller does not belong to: saved bare, since onboarding enrols the caller. */
    private String otherTenant;

    @BeforeEach
    void createTenants() throws Exception {
      memberTenant =
          tenantHelper
              .createTenantWithCapabilities(
                  "implant-member", Set.of(Capability.AGENT_RUNTIME_ACCESS))
              .getId();
      otherTenant =
          tenantRepository
              .save(TenantFixture.getTenant("implant-other-" + UUID.randomUUID()))
              .getId();
    }

    @AfterEach
    void deleteTenants() {
      tenantHelper.deleteCommittedTenants(memberTenant, otherTenant);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Endpoint.class)
    @DisplayName("is served for an inject of its own tenant")
    void given_agentAndInjectOfTheCallersTenant_should_beServed(Endpoint endpoint)
        throws Exception {
      // Arrange
      Seeded seeded = inTenant(memberTenant, InjectImplantTenantScopeTest.this::seed);

      // Act
      ResultActions result = perform(endpoint, Route.PREFIXED, memberTenant, seeded);

      // Assert
      result.andExpect(status().is2xxSuccessful());
      assertThat(executionTraceCount(seeded.injectId())).isPositive();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Endpoint.class)
    @DisplayName("is refused, and writes nothing, for an inject of another tenant")
    void given_injectOfAnotherTenantTargetingTheCallersAgent_should_refuseAndWriteNothing(
        Endpoint endpoint) throws Exception {
      // Arrange: the agent and its asset are in the caller's tenant, so the agent-to-inject check
      // alone lets the request through; only the inject is in another tenant.
      Seeded seeded = inTenant(otherTenant, InjectImplantTenantScopeTest.this::seed);
      moveAgentAndAssetTo(seeded, memberTenant);

      // Act
      ResultActions result = perform(endpoint, Route.PREFIXED, memberTenant, seeded);

      // Assert
      assertAgentAccessDenied(result);
      assertThat(executionTraceCount(seeded.injectId())).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Endpoint.class)
    @DisplayName("is refused for an agent and an inject of another tenant")
    void given_agentAndInjectOfAnotherTenant_should_refuseAndWriteNothing(Endpoint endpoint)
        throws Exception {
      // Arrange
      Seeded seeded = inTenant(otherTenant, InjectImplantTenantScopeTest.this::seed);

      // Act
      ResultActions result = perform(endpoint, Route.PREFIXED, memberTenant, seeded);

      // Assert
      assertAgentAccessDenied(result);
      assertThat(executionTraceCount(seeded.injectId())).isZero();
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource(UNPREFIXED_CALLS)
    @DisplayName("is refused on a route that sets no ambient tenant")
    void given_nonPrefixedRoute_should_refuseAnAgentOfANonDefaultTenant(
        Endpoint endpoint, Route route) throws Exception {
      // Arrange: the capability is held in the caller's tenant, and the ambient tenant of this
      // route is the default one.
      Seeded seeded = inTenant(memberTenant, InjectImplantTenantScopeTest.this::seed);

      // Act
      ResultActions result = perform(endpoint, route, memberTenant, seeded);

      // Assert
      result.andExpect(status().isForbidden());
      assertThat(executionTraceCount(seeded.injectId())).isZero();
    }
  }

  /**
   * A caller whose only membership is the default tenant, on the three routes. It is an admin: the
   * capability check lets it through, so the scope guard is the only tenant check it meets.
   */
  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("A caller of the default tenant")
  class DefaultTenantCaller {

    private String otherTenant;
    private final List<Seeded> moved = new ArrayList<>();

    @BeforeEach
    void createTenant() {
      otherTenant =
          tenantRepository
              .save(TenantFixture.getTenant("implant-other-" + UUID.randomUUID()))
              .getId();
      tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    }

    @AfterEach
    void deleteTenant() {
      // The agents and assets moved into the default tenant go back before the tenant is deleted.
      moved.forEach(seeded -> moveAgentAndAssetTo(seeded, otherTenant));
      tenantHelper.detachCurrentUserFromTenant(Tenant.DEFAULT_TENANT_UUID);
      tenantHelper.deleteCommittedTenants(otherTenant);
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource(CALLS)
    @DisplayName("is refused, and writes nothing, for an inject of another tenant")
    void given_injectOfAnotherTenantTargetingTheCallersAgent_should_refuseAndWriteNothing(
        Endpoint endpoint, Route route) throws Exception {
      // Arrange
      Seeded seeded = inTenant(otherTenant, InjectImplantTenantScopeTest.this::seed);
      moveAgentAndAssetTo(seeded, Tenant.DEFAULT_TENANT_UUID);
      moved.add(seeded);

      // Act
      ResultActions result = perform(endpoint, route, Tenant.DEFAULT_TENANT_UUID, seeded);

      // Assert
      assertAgentAccessDenied(result);
      assertThat(executionTraceCount(seeded.injectId())).isZero();
    }
  }

  /** The default tenant's agent, the one every pre-multi-tenancy installation holds. */
  @Nested
  @Transactional
  @WithMockUser
  @DisplayName("An agent of the default tenant")
  class DefaultTenant {

    @BeforeEach
    void joinTheDefaultTenant() {
      tenantHelper.grantCapabilitiesInTenant(
          Tenant.DEFAULT_TENANT_UUID, Set.of(Capability.AGENT_RUNTIME_ACCESS));
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource(CALLS)
    @DisplayName("is served for an inject of the default tenant")
    void given_agentAndInjectOfTheDefaultTenant_should_beServed(Endpoint endpoint, Route route)
        throws Exception {
      // Arrange
      Seeded seeded = seed();
      entityManager.flush();
      entityManager.clear();

      // Act
      ResultActions result = perform(endpoint, route, Tenant.DEFAULT_TENANT_UUID, seeded);

      // Assert
      result.andExpect(status().is2xxSuccessful());
      entityManager.flush();
      assertThat(executionTraceCount(seeded.injectId())).isPositive();
    }
  }

  /**
   * A caller that holds the runtime capability in two tenants (A and B), with no capability in the
   * default tenant. Only the prefixed route lets it past {@code @AccessControl}: the non-prefixed
   * route checks the capability in the ambient tenant, which is the default one, before it looks at
   * the scope. The scope on the prefixed route is the path tenant, never the union of the
   * memberships.
   */
  @Nested
  @WithMockUser
  @DisplayName("A caller with the runtime capability in two tenants, none in the default one")
  class MultiMembershipCaller {

    private String tenantA;
    private String tenantB;
    private String tenantC;

    @BeforeEach
    void createTenants() throws Exception {
      tenantA =
          tenantHelper
              .createTenantWithCapabilities("implant-a", Set.of(Capability.AGENT_RUNTIME_ACCESS))
              .getId();
      tenantB =
          tenantHelper
              .createTenantWithCapabilities("implant-b", Set.of(Capability.AGENT_RUNTIME_ACCESS))
              .getId();
      tenantC =
          tenantRepository.save(TenantFixture.getTenant("implant-c-" + UUID.randomUUID())).getId();
    }

    @AfterEach
    void deleteTenants() {
      tenantHelper.deleteCommittedTenants(tenantA, tenantB, tenantC);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Endpoint.class)
    @DisplayName("is served for an inject of the tenant of the path")
    void given_prefixedRouteOfOneMembership_should_serveAnInjectOfThatTenant(Endpoint endpoint)
        throws Exception {
      // Arrange
      Seeded seeded = inTenant(tenantB, InjectImplantTenantScopeTest.this::seed);

      // Act
      ResultActions result = perform(endpoint, Route.PREFIXED, tenantB, seeded);

      // Assert
      result.andExpect(status().is2xxSuccessful());
      assertThat(executionTraceCount(seeded.injectId())).isPositive();
    }

    @ParameterizedTest(name = "{0} on an inject of tenant {1}")
    @MethodSource("io.openaev.rest.inject.InjectImplantTenantScopeTest#endpointsAndOtherTenants")
    @DisplayName("is refused, and writes nothing, for an inject outside the tenant of the path")
    void given_prefixedRoute_should_refuseAnInjectOutsideThePathTenant(
        Endpoint endpoint, String otherTenant) throws Exception {
      // Arrange: the agent and its asset are in the path tenant, so only the inject is outside it.
      // "A" is a tenant the caller belongs to, "C" one it does not.
      String injectTenant = "A".equals(otherTenant) ? tenantA : tenantC;
      Seeded seeded = inTenant(injectTenant, InjectImplantTenantScopeTest.this::seed);
      moveAgentAndAssetTo(seeded, tenantB);

      // Act
      ResultActions result = perform(endpoint, Route.PREFIXED, tenantB, seeded);

      // Assert
      assertAgentAccessDenied(result);
      assertThat(executionTraceCount(seeded.injectId())).isZero();
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource(UNPREFIXED_CALLS)
    @DisplayName("is refused on a route that sets no ambient tenant, even for its own tenant")
    void given_nonPrefixedRoute_should_refuseBeforeTheScopeIsRead(Endpoint endpoint, Route route)
        throws Exception {
      // Arrange
      Seeded seeded = inTenant(tenantB, InjectImplantTenantScopeTest.this::seed);

      // Act
      ResultActions result = perform(endpoint, route, tenantB, seeded);

      // Assert
      result.andExpect(status().isForbidden());
      assertThat(executionTraceCount(seeded.injectId())).isZero();
    }
  }

  /**
   * The same caller with the capability in the default tenant too: the ambient tenant of the
   * non-prefixed route then passes the capability check, and the scope is the whole membership set
   * (no selector) or the tenant {@code X-Tenant-Ids} names.
   */
  @Nested
  @WithMockUser
  @DisplayName("A caller with the runtime capability in the default tenant and in two others")
  class MultiMembershipCallerWithDefault {

    private String tenantA;
    private String tenantB;
    private String tenantC;
    private List<String> defaultGroupIds;
    private List<String> defaultRoleIds;

    @BeforeEach
    void createTenants() throws Exception {
      tenantA =
          tenantHelper
              .createTenantWithCapabilities("implant-a", Set.of(Capability.AGENT_RUNTIME_ACCESS))
              .getId();
      tenantB =
          tenantHelper
              .createTenantWithCapabilities("implant-b", Set.of(Capability.AGENT_RUNTIME_ACCESS))
              .getId();
      tenantC =
          tenantRepository.save(TenantFixture.getTenant("implant-c-" + UUID.randomUUID())).getId();
      tenantHelper.grantCapabilitiesInTenant(
          Tenant.DEFAULT_TENANT_UUID, Set.of(Capability.AGENT_RUNTIME_ACCESS));
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      String userId = testUserHolder.get().getId();
      defaultGroupIds =
          jdbc.queryForList(
              "SELECT g.group_id FROM groups g JOIN users_groups ug ON ug.group_id = g.group_id"
                  + " WHERE g.tenant_id = ? AND ug.user_id = ?",
              String.class,
              Tenant.DEFAULT_TENANT_UUID,
              userId);
      defaultRoleIds =
          defaultGroupIds.isEmpty()
              ? List.of()
              : jdbc.queryForList(
                  "SELECT DISTINCT gr.role_id FROM groups_roles gr WHERE gr.group_id IN ("
                      + String.join(",", Collections.nCopies(defaultGroupIds.size(), "?"))
                      + ")",
                  String.class,
                  defaultGroupIds.toArray());
    }

    @AfterEach
    void deleteTenants() {
      // The role and group granted in the default tenant are committed: remove them.
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      defaultGroupIds.forEach(
          id -> {
            jdbc.update("DELETE FROM users_groups WHERE group_id = ?", id);
            jdbc.update("DELETE FROM groups_roles WHERE group_id = ?", id);
            jdbc.update("DELETE FROM groups WHERE group_id = ?", id);
          });
      defaultRoleIds.forEach(
          id -> {
            jdbc.update("DELETE FROM roles_capabilities WHERE role_id = ?", id);
            jdbc.update("DELETE FROM roles WHERE role_id = ?", id);
          });
      tenantHelper.detachCurrentUserFromTenant(Tenant.DEFAULT_TENANT_UUID);
      tenantHelper.deleteCommittedTenants(tenantA, tenantB, tenantC);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Endpoint.class)
    @DisplayName("is served, with no selector, for an inject of one of its tenants")
    void given_noSelector_should_serveAnInjectOfAMembershipTenant(Endpoint endpoint)
        throws Exception {
      // Arrange
      Seeded seeded = inTenant(tenantB, InjectImplantTenantScopeTest.this::seed);

      // Act
      ResultActions result = perform(endpoint, Route.NON_PREFIXED, tenantB, seeded);

      // Assert
      result.andExpect(status().is2xxSuccessful());
      assertThat(executionTraceCount(seeded.injectId())).isPositive();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Endpoint.class)
    @DisplayName("is refused, with no selector, for an inject of a tenant it does not belong to")
    void given_noSelector_should_refuseAnInjectOfAForeignTenant(Endpoint endpoint)
        throws Exception {
      // Arrange: the agent and its asset are in a membership tenant, so only the inject is foreign.
      Seeded seeded = inTenant(tenantC, InjectImplantTenantScopeTest.this::seed);
      moveAgentAndAssetTo(seeded, tenantB);

      // Act
      ResultActions result = perform(endpoint, Route.NON_PREFIXED, tenantB, seeded);

      // Assert
      assertAgentAccessDenied(result);
      assertThat(executionTraceCount(seeded.injectId())).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Endpoint.class)
    @DisplayName("is refused when the selector names another tenant than the one of the inject")
    void given_selectorOfOneTenant_should_refuseAnInjectOfAnotherMembershipTenant(Endpoint endpoint)
        throws Exception {
      // Arrange: both tenants are memberships of the caller, the selector narrows the scope to A.
      Seeded seeded = inTenant(tenantB, InjectImplantTenantScopeTest.this::seed);
      moveAgentAndAssetTo(seeded, tenantA);

      // Act
      ResultActions result = perform(endpoint, Route.SELECTOR_HEADER, tenantA, seeded);

      // Assert
      assertAgentAccessDenied(result);
      assertThat(executionTraceCount(seeded.injectId())).isZero();
    }
  }

  /**
   * The callback holds the per-inject lock only once the caller has been let in: a refused call
   * never takes it, and a served one still does, so the callbacks of one inject stay serialised.
   */
  @Nested
  @WithMockUser
  @DisplayName("The per-inject lock of the agent callback")
  class PerInjectLock {

    private String memberTenant;
    private String otherTenant;

    @BeforeEach
    void createTenants() throws Exception {
      memberTenant =
          tenantHelper
              .createTenantWithCapabilities("implant-lock", Set.of(Capability.AGENT_RUNTIME_ACCESS))
              .getId();
      otherTenant =
          tenantRepository
              .save(TenantFixture.getTenant("implant-other-" + UUID.randomUUID()))
              .getId();
    }

    @AfterEach
    void deleteTenants() {
      tenantHelper.deleteCommittedTenants(memberTenant, otherTenant);
    }

    @Test
    @DisplayName("is not taken by a refused callback")
    void given_injectOutsideTheScope_should_refuseWhileTheLockIsHeldElsewhere() throws Exception {
      // Arrange: another thread holds the lock of the inject for the whole request
      Seeded seeded = inTenant(otherTenant, InjectImplantTenantScopeTest.this::seed);
      moveAgentAndAssetTo(seeded, memberTenant);

      // Act: a request that waited for the lock would only return once the holder had left it
      boolean heldWhenAnswered = holdingTheLockWhileAnswering(seeded, memberTenant);

      // Assert
      assertThat(heldWhenAnswered).as("the refusal came back while the lock was held").isTrue();
      assertThat(executionTraceCount(seeded.injectId())).isZero();
    }

    @Test
    @DisplayName("is taken by a served callback")
    void given_injectInTheScope_should_waitForTheLockOfTheInject() throws Exception {
      // Arrange
      Seeded seeded = inTenant(memberTenant, InjectImplantTenantScopeTest.this::seed);

      // Act
      boolean heldWhenAnswered = holdingTheLockWhileAnswering(seeded, memberTenant);

      // Assert: the callback only ran after the holder had left the lock
      assertThat(heldWhenAnswered).as("the served callback waited for the lock").isFalse();
      assertThat(executionTraceCount(seeded.injectId())).isPositive();
    }

    /**
     * Whether the lock of the inject was still held by the other thread when the callback of the
     * agent was answered. The holder gives the lock up after a few seconds, so a call that needs it
     * is delayed, never blocked for good.
     */
    private boolean holdingTheLockWhileAnswering(Seeded seeded, String callerTenant)
        throws Exception {
      CountDownLatch held = new CountDownLatch(1);
      AtomicBoolean stillHolding = new AtomicBoolean();
      ExecutorService holder = Executors.newSingleThreadExecutor();
      try {
        holder.submit(
            () -> {
              lockHolder.hold(seeded.injectId(), held, stillHolding);
              return null;
            });
        assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();

        perform(Endpoint.CALLBACK, Route.PREFIXED, callerTenant, seeded);

        return stillHolding.get();
      } finally {
        holder.shutdown();
        assertThat(holder.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
      }
    }
  }

  /** A bean that holds the per-inject lock of the callback, the way the endpoint takes it. */
  static class LockHolder {
    private static final long HOLD_SECONDS = 3;

    @Lock(type = LockResourceType.INJECT, key = "#injectId")
    public void hold(String injectId, CountDownLatch held, AtomicBoolean stillHolding)
        throws InterruptedException {
      stillHolding.set(true);
      held.countDown();
      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(HOLD_SECONDS));
      } finally {
        stillHolding.set(false);
      }
    }
  }

  @TestConfiguration
  static class LockHolderConfig {
    @Bean
    LockHolder lockHolder() {
      return new LockHolder();
    }
  }

  static Stream<Arguments> endpointsAndOtherTenants() {
    return Stream.of(Endpoint.values())
        .flatMap(endpoint -> Stream.of("A", "C").map(tenant -> Arguments.of(endpoint, tenant)));
  }

  // -- helpers --
  // The fixtures stamp the tenant of the current context: call seed() inside inTenant().

  private Seeded seed() {
    AgentComposer.Composer agent = agentComposer.forAgent(AgentFixture.createDefaultAgentService());
    EndpointComposer.Composer endpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).withAgent(agent);
    InjectComposer.Composer inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .withEndpoint(endpoint)
            .withInjectorContract(
                injectorContractComposer
                    .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                    .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
                    .withInjector(InjectorFixture.createDefaultPayloadInjector())
                    .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand())))
            .persist();
    return new Seeded(agent.get().getId(), inject.get().getId(), endpoint.get().getId());
  }

  /** The agent and its asset of another tenant than the inject that targets them. */
  private void moveAgentAndAssetTo(Seeded seeded, String tenantId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update("UPDATE agents SET tenant_id = ? WHERE agent_id = ?", tenantId, seeded.agentId());
    jdbc.update(
        "UPDATE assets SET tenant_id = ? WHERE asset_id = ?", tenantId, seeded.endpointId());
  }

  /** The refusal of the agent checks: an unknown inject gets the same one. */
  private static void assertAgentAccessDenied(ResultActions result) throws Exception {
    result
        .andExpect(status().isForbidden())
        .andExpect(
            mvcResult ->
                assertThat(mvcResult.getResolvedException())
                    .isInstanceOf(ForbiddenException.class));
  }

  private int executionTraceCount(String injectId) {
    return new JdbcTemplate(dataSource)
        .queryForObject(
            "SELECT count(*) FROM execution_traces t"
                + " JOIN injects_statuses s ON t.execution_inject_status_id = s.status_id"
                + " WHERE s.status_inject = ?",
            Integer.class,
            injectId);
  }

  /**
   * The request an implant sends, on one of the three ways to reach the platform. No fixture leaves
   * a tenant on the thread of the request: the non-prefixed route runs with no ambient tenant, as
   * in production.
   */
  private ResultActions perform(Endpoint endpoint, Route route, String callerTenant, Seeded seeded)
      throws Exception {
    TenantContext.clearCurrentTenant();
    String base = route == Route.PREFIXED ? tenantInjectUri(callerTenant) : INJECT_URI;
    MockHttpServletRequestBuilder request =
        switch (endpoint) {
          case CALLBACK ->
              post(base + "/execution/" + seeded.agentId() + "/callback/" + seeded.injectId())
                  .content(asJsonString(traceInput()))
                  .contentType(MediaType.APPLICATION_JSON);
          case EXECUTABLE_PAYLOAD ->
              get(base + "/" + seeded.injectId() + "/" + seeded.agentId() + "/executable-payload");
        };
    if (route == Route.SELECTOR_HEADER) {
      request.header("X-Tenant-Ids", callerTenant);
    }
    return mvc.perform(request.accept(MediaType.APPLICATION_JSON).with(csrf()));
  }

  private static String tenantInjectUri(String tenantId) {
    return TENANT_INJECT_URI.replace("{tenantId}", tenantId);
  }

  private static InjectExecutionInput traceInput() {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setMessage("trace");
    input.setAction(InjectExecutionAction.command_execution);
    input.setStatus("SUCCESS");
    return input;
  }

  private <T> T inTenant(String tenantId, Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
