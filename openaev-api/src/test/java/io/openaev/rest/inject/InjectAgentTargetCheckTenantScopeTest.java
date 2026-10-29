package io.openaev.rest.inject;

import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.service.InjectExecutionCallbackService;
import io.openaev.service.queue.BatchQueueService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.KeepRabbit;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The implant target check ({@code InjectService#checkAgentTargetsInject}) must follow the
 * transaction's {@code TxCtx}, not the v1 {@code TenantContext} thread-local.
 *
 * <p>The implant callback is served on the non-prefixed {@code /api/injects/...} route: there is no
 * {@code {tenantId}} path variable, so {@code TenantInterceptor} never sets the thread-local and
 * {@code TenantContext.getCurrentTenant()} falls back to the default tenant, while {@code
 * TxCtxArgumentResolver} resolves the scope from the caller's memberships. A check rooted on a v1
 * filtered entity ({@code Agent}, {@code Inject}) therefore rejected every valid callback of an
 * agent living in a non-default tenant.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the data is committed in its own tenant
 * through {@link TenantScopedTransaction}, so the request resolves its scope exactly as in
 * production. Tenant deletion cascades to every row seeded here.
 */
@KeepRabbit
@TestPropertySource(properties = "openaev.tenant.active-tables=assets,asset_groups")
@WithMockUser(isAdmin = true)
@DisplayName(
    "Implant agent target check follows the caller's tenant scope on the non-prefixed route")
class InjectAgentTargetCheckTenantScopeTest extends IntegrationTest {

  private record Seeded(String agentId, String injectId) {}

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectExecutionCallbackService injectExecutionCallbackService;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;

  private BatchQueueService<InjectExecutionCallback> savedQueueService;
  private BatchQueueService<InjectExecutionCallback> queue;
  private String tenantA;
  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    savedQueueService = injectExecutionCallbackService.getInjectTraceQueueService();
    queue = mockQueue();
    injectExecutionCallbackService.setInjectTraceQueueService(queue);
    // The caller is a member of tenant A only: its resolved scope is {A}, never the default tenant.
    tenantA = tenantHelper.createTenantWithCurrentUser("agent-target-a").getId();
    // Tenant onboarding also attaches the current user to the tenant it creates: tenant B must be
    // one the caller does not belong to.
    tenantB = tenantHelper.createTenant("agent-target-b").getId();
    String userId = testUserHolder.get().getId();
    new JdbcTemplate(dataSource)
        .update("DELETE FROM users_tenants WHERE user_id = ? AND tenant_id = ?", userId, tenantB);
    tenantMembershipCacheManager.evict(userId, tenantB);
  }

  @AfterEach
  void cleanup() {
    injectExecutionCallbackService.setInjectTraceQueueService(savedQueueService);
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("accepts the callback of an agent whose endpoint is a direct target, in tenant A")
  void given_directTargetInNonDefaultTenant_should_acceptCallback() throws Exception {
    // -- PREPARE --
    Seeded seeded =
        inTenant(
            tenantA,
            () -> {
              AgentComposer.Composer agent = newAgent();
              return persist(agent, newInject().withEndpoint(endpointWith(agent)));
            });

    // -- EXECUTE & ASSERT --
    performCallback(seeded).andExpect(status().is2xxSuccessful());
    verify(queue).publish(any());
  }

  @Test
  @DisplayName("accepts the callback of an agent targeted through a static group, in tenant A")
  void given_staticGroupInNonDefaultTenant_should_acceptCallback() throws Exception {
    // -- PREPARE --
    Seeded seeded =
        inTenant(
            tenantA,
            () -> {
              AgentComposer.Composer agent = newAgent();
              return persist(
                  agent,
                  newInject()
                      .withAssetGroup(
                          assetGroupComposer
                              .forAssetGroup(
                                  AssetGroupFixture.createDefaultAssetGroup("static group"))
                              .withAsset(endpointWith(agent))));
            });

    // -- EXECUTE & ASSERT --
    performCallback(seeded).andExpect(status().is2xxSuccessful());
    verify(queue).publish(any());
  }

  @Test
  @DisplayName("rejects the callback of an agent that is not a target of the inject, in tenant A")
  void given_agentNotTargetedInNonDefaultTenant_should_forbidCallback() throws Exception {
    // -- PREPARE --
    Seeded seeded =
        inTenant(
            tenantA,
            () -> {
              AgentComposer.Composer stranger = newAgent();
              endpointWith(stranger).persist();
              AgentComposer.Composer targeted = newAgent();
              return persist(stranger, newInject().withEndpoint(endpointWith(targeted)));
            });

    // -- EXECUTE & ASSERT --
    performCallback(seeded).andExpect(status().isForbidden());
    verify(queue, never()).publish(any());
  }

  @Test
  @DisplayName("rejects the callback of an agent and inject of a tenant the caller is not in")
  void given_agentAndInjectOfAnotherTenant_should_forbidCallback() throws Exception {
    // -- PREPARE --
    Seeded seeded =
        inTenant(
            tenantB,
            () -> {
              AgentComposer.Composer agent = newAgent();
              return persist(agent, newInject().withEndpoint(endpointWith(agent)));
            });

    // -- EXECUTE & ASSERT --
    performCallback(seeded).andExpect(status().isForbidden());
    verify(queue, never()).publish(any());
  }

  // -- helpers --
  // The fixtures below stamp the tenant of the current context: call them inside inTenant().

  private AgentComposer.Composer newAgent() {
    return agentComposer.forAgent(AgentFixture.createDefaultAgentService());
  }

  private EndpointComposer.Composer endpointWith(AgentComposer.Composer agent) {
    return endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).withAgent(agent);
  }

  private InjectComposer.Composer newInject() {
    return injectComposer
        .forInject(InjectFixture.getInjectWithoutContract())
        .withInjectorContract(
            injectorContractComposer
                .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                .withInjector(
                    InjectorFixture.createDefaultInjector("agent-target-" + UUID.randomUUID())));
  }

  private Seeded persist(AgentComposer.Composer agent, InjectComposer.Composer inject) {
    String injectId = inject.persist().get().getId();
    return new Seeded(agent.get().getId(), injectId);
  }

  /** The non-prefixed implant route: no {tenantId} path variable, so no v1 thread-local. */
  private ResultActions performCallback(Seeded seeded) throws Exception {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setMessage("trace");
    input.setAction(InjectExecutionAction.command_execution);
    input.setStatus("SUCCESS");
    return mvc.perform(
        post(INJECT_URI + "/execution/" + seeded.agentId() + "/callback/" + seeded.injectId())
            .content(asJsonString(input))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));
  }

  private <T> T inTenant(String tenantId, Supplier<T> work) {
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static BatchQueueService<InjectExecutionCallback> mockQueue() {
    return mock(BatchQueueService.class);
  }
}
