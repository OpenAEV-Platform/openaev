package io.openaev.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.execution.ExecutionTraceScopeSeed.Tenant;
import io.openaev.scheduler.jobs.ExecutionTraceRetentionJob;
import io.openaev.scheduler.jobs.InjectsFinalizationJob;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
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

/**
 * The execution trace readers, the retention job and the job writers with {@code execution_traces}
 * among the active tables, as the activation will have it. Two tenants each own a pending inject
 * and its traces; every path runs the way production runs it, with no transaction and no ambient
 * tenant of its own on the thread, and the answer is read back from the tenant column.
 *
 * <p>NOT {@code @Transactional}: the scoped primitive refuses to open inside an active transaction,
 * and the jobs commit their own transactions. The rows are committed through JDBC and swept after
 * each test.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Execution traces with the table armed: readers, retention and job writers")
class ExecutionTraceScopedPathsTest extends IntegrationTest {

  private static final int RETENTION_DAYS = 30;
  private static final int OLD_DAYS = 100;
  private static final int RECENT_DAYS = 5;

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    // The production list with execution_traces on it, as the activation will have it
    registry.add(
        "openaev.tenant.active-tables",
        () -> {
          TreeSet<String> tables = new TreeSet<>(ProductionActiveTables.names());
          tables.add("execution_traces");
          return String.join(",", tables);
        });
  }

  @Autowired private ExecutionTraceRepository executionTraceRepository;
  @Autowired private ExecutionTraceRetentionJob retentionJob;
  @Autowired private InjectsFinalizationJob finalizationJob;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

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
}
