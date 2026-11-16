package io.openaev.execution;

import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.rest.inject.InjectApi.TENANT_INJECT_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.execution.ExecutionTraceScopeSeed.Tenant;
import io.openaev.scheduler.jobs.ExecutionTraceRetentionJob;
import io.openaev.scheduler.jobs.InjectsFinalizationJob;
import io.openaev.service.chaining.WorkflowEndService;
import io.openaev.service.chaining.WorkflowEndService.WORKFLOW_END_CAUSE;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The execution trace readers, the retention job and the job writers on the production list of
 * active tables, which holds {@code execution_traces}. Two tenants each own a pending inject and
 * its traces; every path runs the way production runs it, with no transaction and no ambient tenant
 * of its own on the thread, and the answer is read back from the tenant column. The execution log
 * of an inject is read over HTTP on the tenant route and on the {@code X-Tenant-Ids} route.
 *
 * <p>NOT {@code @Transactional}: the scoped primitive refuses to open inside an active transaction,
 * and the jobs commit their own transactions. The rows are committed through JDBC and swept after
 * each test.
 */
@CoversTenantIsolation("execution_traces")
@WithMockUser(isAdmin = true)
@DisplayName("Execution traces with the table armed: readers, retention and job writers")
class ExecutionTraceScopedPathsTest extends IntegrationTest {

  private static final int RETENTION_DAYS = 30;
  private static final int OLD_DAYS = 100;
  private static final int RECENT_DAYS = 5;

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private ExecutionTraceRepository executionTraceRepository;
  @Autowired private ExecutionTraceRetentionJob retentionJob;
  @Autowired private InjectsFinalizationJob finalizationJob;
  @Autowired private ExecutionExecutorService executionExecutorService;
  @Autowired private WorkflowEndService workflowEndService;
  @Autowired private InjectStatusRepository injectStatusRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;
  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private ExecutionTraceScopeSeed seed;
  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void seedTwoTenants() {
    seed = new ExecutionTraceScopeSeed(new JdbcTemplate(dataSource));
    tenantA = seed.seedTenant("a");
    tenantB = seed.seedTenant("b");
  }

  @AfterEach
  void sweep() {
    seed.sweep();
  }

  /** Runs the work the way a Quartz worker does: a bare thread, no session, no ambient tenant. */
  private static void onBareThread(Runnable work) throws InterruptedException {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                work.run();
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    thread.start();
    thread.join();
    if (failure.get() != null) {
      throw new AssertionError("the work failed", failure.get());
    }
  }

  /**
   * The messages of the traces of a tenant's inject status, read through the status's eager trace
   * collection under {@code ctx}. The status table is not active, so the status is found in any
   * scope and only the trace rows depend on it.
   */
  private List<String> statusTraceMessages(TxCtx ctx, Tenant tenant) {
    return tenantTx.execute(
        ctx,
        () ->
            injectStatusRepository.findById(tenant.statusId()).orElseThrow().getTraces().stream()
                .map(ExecutionTrace::getMessage)
                .toList());
  }

  static Stream<Arguments> finders() {
    return Stream.of(
        Arguments.of(
            "agent",
            (BiFunction<ExecutionTraceRepository, Tenant, List<ExecutionTrace>>)
                (repo, t) -> repo.findByInjectIdAndAgentId(t.injectId(), t.agentId())),
        Arguments.of(
            "asset",
            (BiFunction<ExecutionTraceRepository, Tenant, List<ExecutionTrace>>)
                (repo, t) -> repo.findByInjectIdAndAssetId(t.injectId(), t.assetId())),
        Arguments.of(
            "team",
            (BiFunction<ExecutionTraceRepository, Tenant, List<ExecutionTrace>>)
                (repo, t) -> repo.findByInjectIdAndTeamId(t.injectId(), t.teamId())),
        Arguments.of(
            "player",
            (BiFunction<ExecutionTraceRepository, Tenant, List<ExecutionTrace>>)
                (repo, t) -> repo.findByInjectIdAndPlayerId(t.injectId(), t.playerId())));
  }

  /**
   * The complete execution log of an inject, {@code GET .../injects/status-all-traces}, on the
   * tenant route and on the non-prefixed route with an {@code X-Tenant-Ids} selector. Each route
   * builds the request for a scope tenant and an inject.
   */
  static Stream<Arguments> executionLogRoutes() {
    return Stream.of(
        Arguments.of(
            "tenant route",
            (BinaryOperator<String>)
                (scopeTenant, injectId) ->
                    TENANT_INJECT_URI.replace("{tenantId}", scopeTenant) + "/status-all-traces",
            false),
        Arguments.of(
            "X-Tenant-Ids route",
            (BinaryOperator<String>) (scopeTenant, injectId) -> INJECT_URI + "/status-all-traces",
            true));
  }

  @Nested
  @DisplayName("Readers")
  class Readers {

    @BeforeEach
    void seedTraces() {
      for (Tenant tenant : List.of(tenantA, tenantB)) {
        // One trace answers all four finders: its agent, the asset of that agent, and the player
        // of the team, named in its context identifiers
        seed.insertTrace(
            tenant,
            tenant.agentId(),
            "EXECUTION",
            "INFO",
            RECENT_DAYS,
            tenant.assetId(),
            tenant.playerId());
      }
    }

    private List<String> readUnder(
        TxCtx ctx,
        BiFunction<ExecutionTraceRepository, Tenant, List<ExecutionTrace>> find,
        Tenant tenant) {
      return tenantTx.execute(
          ctx,
          () ->
              find.apply(executionTraceRepository, tenant).stream()
                  .map(ExecutionTrace::getId)
                  .toList());
    }

    @ParameterizedTest(name = "{0} finder")
    @MethodSource("io.openaev.execution.ExecutionTraceScopedPathsTest#finders")
    @DisplayName("A finder returns the trace of its inject in the scope of the inject's tenant")
    void given_scopeOfTheInjectTenant_should_returnItsTrace(
        String name, BiFunction<ExecutionTraceRepository, Tenant, List<ExecutionTrace>> find) {
      // Arrange
      TxCtx scopeA = TxCtx.forTenants(List.of(tenantA.id()));
      TxCtx scopeB = TxCtx.forTenants(List.of(tenantB.id()));

      // Act
      List<String> ofA = readUnder(scopeA, find, tenantA);
      List<String> ofB = readUnder(scopeB, find, tenantB);

      // Assert
      assertThat(ofA).hasSize(1);
      assertThat(ofB).hasSize(1);
      assertThat(ofA).doesNotContainAnyElementsOf(ofB);
    }

    @ParameterizedTest(name = "{0} finder")
    @MethodSource("io.openaev.execution.ExecutionTraceScopedPathsTest#finders")
    @DisplayName("A finder returns nothing for an inject of a tenant outside the scope")
    void given_scopeOfAnotherTenant_should_returnNothing(
        String name, BiFunction<ExecutionTraceRepository, Tenant, List<ExecutionTrace>> find) {
      // Arrange
      TxCtx scopeB = TxCtx.forTenants(List.of(tenantB.id()));

      // Act
      List<String> ofAFromB = readUnder(scopeB, find, tenantA);

      // Assert
      assertThat(ofAFromB).isEmpty();
    }
  }

  @Nested
  @DisplayName("HTTP readers on both routes")
  class HttpReaders {

    @BeforeEach
    void callerInBothTenants() {
      tenantHelper.attachCurrentUserToTenant(tenantA.id());
      tenantHelper.attachCurrentUserToTenant(tenantB.id());
    }

    @AfterEach
    void callerLeavesBothTenants() {
      tenantHelper.detachCurrentUserFromTenant(tenantA.id());
      tenantHelper.detachCurrentUserFromTenant(tenantB.id());
    }

    /**
     * Reads the execution log from an empty ambient tenant, as a production request thread starts:
     * the fixtures leave a tenant on the test thread, which the non-prefixed route would read.
     */
    private MvcResult readExecutionLog(
        BinaryOperator<String> uri, boolean header, String scopeTenant, String injectId)
        throws Exception {
      MockHttpServletRequestBuilder request =
          get(uri.apply(scopeTenant, injectId)).param("injectId", injectId);
      if (header) {
        request.header("X-Tenant-Ids", scopeTenant);
      }
      TenantContext.clearCurrentTenant();
      return mvc.perform(request).andReturn();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.execution.ExecutionTraceScopedPathsTest#executionLogRoutes")
    @DisplayName("The execution log of an inject holds its trace in the scope of its tenant")
    void given_scopeOfTheInjectTenant_should_returnItsTraceInTheExecutionLog(
        String name, BinaryOperator<String> uri, boolean header) throws Exception {
      // Arrange
      seed.insertTrace(tenantA, null, "START", "INFO", RECENT_DAYS);
      seed.insertTrace(tenantB, null, "COMPLETE", "EXECUTED", RECENT_DAYS);

      // Act
      MvcResult result = readExecutionLog(uri, header, tenantB.id(), tenantB.injectId());

      // Assert
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      assertThatJson(result.getResponse().getContentAsString())
          .inPath("$.status_main_traces[*].execution_action")
          .isArray()
          .containsExactly("COMPLETE");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.execution.ExecutionTraceScopedPathsTest#executionLogRoutes")
    @DisplayName("The execution log of an inject is refused in the scope of another tenant")
    void given_scopeOfAnotherTenant_should_refuseTheExecutionLog(
        String name, BinaryOperator<String> uri, boolean header) throws Exception {
      // Arrange
      seed.insertTrace(tenantB, null, "COMPLETE", "EXECUTED", RECENT_DAYS);

      // Act
      MvcResult result = readExecutionLog(uri, header, tenantA.id(), tenantB.injectId());

      // Assert
      assertThat(result.getResponse().getStatus()).isEqualTo(404);
      assertThat(result.getResponse().getContentAsString()).doesNotContain("EXECUTED");
    }
  }

  @Nested
  @DisplayName("Retention job")
  class Retention {

    @Test
    @DisplayName("Deletes the old traces of every tenant and leaves the recent ones")
    void given_oldTracesInTwoTenants_should_deleteThemAll() throws Exception {
      // Arrange
      String oldOfA = seed.insertTrace(tenantA, null, "EXECUTION", "INFO", OLD_DAYS);
      String oldOfB = seed.insertTrace(tenantB, null, "EXECUTION", "INFO", OLD_DAYS);
      String recentOfA = seed.insertTrace(tenantA, null, "EXECUTION", "INFO", RECENT_DAYS);
      String recentOfB = seed.insertTrace(tenantB, null, "EXECUTION", "INFO", RECENT_DAYS);
      ReflectionTestUtils.setField(retentionJob, "retentionDays", RETENTION_DAYS);

      try {
        // Act
        onBareThread(
            () -> {
              try {
                retentionJob.execute(null);
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });

        // Assert
        assertThat(seed.traceExists(oldOfA)).isFalse();
        assertThat(seed.traceExists(oldOfB)).isFalse();
        assertThat(seed.traceExists(recentOfA)).isTrue();
        assertThat(seed.traceExists(recentOfB)).isTrue();
      } finally {
        ReflectionTestUtils.setField(retentionJob, "retentionDays", 0);
      }
    }
  }

  @Nested
  @DisplayName("Finalization job writers")
  class FinalizationWriters {

    @Test
    @DisplayName("Stores the timeout trace of each pending inject in the tenant of that inject")
    void given_pendingInjectsInTwoTenants_should_storeEachTimeoutTraceInItsOwnTenant()
        throws Exception {
      // Arrange: the inject of A already holds the COMPLETE trace of its injector, so reading the
      // traces of the status in A's scope decides that no timeout trace is added there
      seed.insertTrace(tenantA, null, "COMPLETE", "EXECUTED", RECENT_DAYS);

      // Act
      onBareThread(finalizationJob::handlePendingInject);

      // Assert
      assertThat(seed.traceTenantsOfStatus(tenantA.statusId())).containsExactly(tenantA.id());
      assertThat(seed.traceTenantsOfStatus(tenantB.statusId())).containsExactly(tenantB.id());
    }
  }

  @Nested
  @DisplayName("Executor launch writers")
  class LaunchWriters {

    /**
     * Runs a launch writer on the seeded status and agent of a tenant, both loaded in that tenant's
     * scope, from an empty ambient tenant: the trace must take its tenant from its inject.
     */
    private void launchInScopeOf(Tenant tenant, BiConsumer<Set<Agent>, InjectStatus> writer) {
      TenantContext.clearCurrentTenant();
      tenantTx.execute(
          TxCtx.forTenants(List.of(tenant.id())),
          () -> {
            InjectStatus status = injectStatusRepository.findById(tenant.statusId()).orElseThrow();
            Agent agent = agentRepository.findById(tenant.agentId()).orElseThrow();
            writer.accept(Set.of(agent), status);
          });
    }

    private List<String> agentTraceIds(TxCtx ctx, Tenant tenant) {
      return tenantTx.execute(
          ctx,
          () ->
              executionTraceRepository
                  .findByInjectIdAndAgentId(tenant.injectId(), tenant.agentId())
                  .stream()
                  .map(ExecutionTrace::getId)
                  .toList());
    }

    @Test
    @DisplayName("Stores the trace of an inactive agent in the tenant of its inject")
    void given_inactiveAgentOfTheInjectTenant_should_storeItsTraceInThatTenant() {
      // Arrange
      TxCtx scopeA = TxCtx.forTenants(List.of(tenantA.id()));
      TxCtx scopeB = TxCtx.forTenants(List.of(tenantB.id()));

      // Act
      launchInScopeOf(tenantA, executionExecutorService::saveInactiveAgentsTraces);

      // Assert
      assertThat(seed.traceTenantsOfStatus(tenantA.statusId())).containsExactly(tenantA.id());
      assertThat(agentTraceIds(scopeA, tenantA)).hasSize(1);
      assertThat(agentTraceIds(scopeB, tenantA)).isEmpty();
    }

    @Test
    @DisplayName("Stores the agentless distribution trace in the tenant of its inject")
    void given_agentsOfTheInjectTenant_should_storeTheDistributionTraceInThatTenant() {
      // Arrange
      TxCtx scopeA = TxCtx.forTenants(List.of(tenantA.id()));
      TxCtx scopeB = TxCtx.forTenants(List.of(tenantB.id()));

      // Act
      launchInScopeOf(tenantA, executionExecutorService::saveDistributionTrace);

      // Assert
      assertThat(seed.traceTenantsOfStatus(tenantA.statusId())).containsExactly(tenantA.id());
      assertThat(statusTraceMessages(scopeA, tenantA))
          .containsExactly("Distributing inject to 1 agent(s) across 1 endpoint(s)");
      assertThat(statusTraceMessages(scopeB, tenantA)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Chaining end writer")
  class ChainingEndWriter {

    @Test
    @DisplayName("Stores the timeout trace of a pending inject in the tenant of that inject")
    void given_pendingInjectAtWorkflowTimeout_should_storeTheTimeoutTraceInItsTenant() {
      // Arrange
      TxCtx scopeA = TxCtx.forTenants(List.of(tenantA.id()));
      TxCtx scopeB = TxCtx.forTenants(List.of(tenantB.id()));
      TenantContext.clearCurrentTenant();

      // Act
      tenantTx.execute(
          scopeA,
          () ->
              workflowEndService.stopActiveInjects(
                  tenantA.simulationId(), WORKFLOW_END_CAUSE.TIMEOUT));

      // Assert
      assertThat(seed.traceTenantsOfStatus(tenantA.statusId())).containsExactly(tenantA.id());
      assertThat(seed.traceTenantsOfStatus(tenantB.statusId())).isEmpty();
      assertThat(statusTraceMessages(scopeA, tenantA))
          .containsExactly("Inject stopped due to simulation timeout.");
      assertThat(statusTraceMessages(scopeB, tenantA)).isEmpty();
    }
  }
}
