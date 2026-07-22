package io.openaev.service.attackpath;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Command;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Write-latency benchmark for the Phase A create, so the perf gate can be re-measured rather than
 * argued. Run it with:
 *
 * <pre>
 * ATTACKPATH_BENCHMARK=true mvn -pl openaev-api test -Dtest=AttackPathCreateBenchmark
 * </pre>
 *
 * <p>It measures the create <b>as production runs it</b>: through {@code onRun}, inside a caller's
 * transaction (the engine's RUN transaction), with the tables tenant-active so the statement
 * inspector is in the loop, and with the write opening its own scoped transaction. Every iteration
 * uses a fresh inject id so each create is a genuine insert rather than a no-op update.
 *
 * <p>This class exists because the first baseline was taken with a benchmark that was never
 * committed: the numbers survived in the notes, the instrument did not, and "re-measure later"
 * became unactionable. Committed and env-gated so CI never runs it but anyone can reproduce it.
 */
@Tag("benchmark")
@EnabledIfEnvironmentVariable(named = "ATTACKPATH_BENCHMARK", matches = "true")
@WithMockUser(isAdmin = true)
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH",
      "openaev.tenant.active-tables=attackpath_execution,attackpath_finding"
    })
@DisplayName("attack path Phase A create: write latency")
class AttackPathCreateBenchmark extends IntegrationTest {

  private static final String SIM = "ap-create-bench";
  private static final int WARMUP = 5;
  private static final int RUNS = 30;

  @Autowired private AttackPathExecutionIngestionService ingestionService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private TransactionTemplate tx;
  private Tenant tenant;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    tx = new TransactionTemplate(transactionManager);
    // Re-entrant on purpose: a benchmark gets interrupted, and a fixed tenant name would then make
    // every later run fail on the unique constraint instead of measuring anything.
    jdbc.update(
        "DELETE FROM attackpath_execution WHERE attackpath_execution_simulation_id = ?", SIM);
    jdbc.update("DELETE FROM tenants WHERE tenant_name LIKE 'ap-create-bench%'");
    tenant =
        tenantRepository.saveAndFlush(
            TenantFixture.getTenant("ap-create-bench-" + UUID.randomUUID()));
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void tearDown() {
    jdbc.update(
        "DELETE FROM attackpath_execution WHERE attackpath_execution_simulation_id = ?", SIM);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenant.getId());
  }

  @Test
  @DisplayName("per-create latency by inject size, and throughput under concurrency")
  void measure() throws Exception {
    System.out.println("\nN | p50 ms | p95 ms | ~ms/row");
    for (int n : new int[] {1, 5, 10, 25, 100}) {
      List<Long> samples = new ArrayList<>();
      for (int i = 0; i < WARMUP + RUNS; i++) {
        long elapsed = timeOneCreate(n);
        if (i >= WARMUP) {
          samples.add(elapsed);
        }
      }
      samples.sort(Long::compareTo);
      double p50 = ms(percentile(samples, 0.50));
      System.out.printf(
          "%d | %.1f | %.1f | %.2f%n", n, p50, ms(percentile(samples, 0.95)), p50 / n);
    }

    System.out.println("\nK | throughput creates/s (N=10)");
    for (int k : new int[] {1, 4, 8}) {
      System.out.printf("%d | %.0f%n", k, throughputAt(k));
    }
  }

  /** One create, timed the way production issues it: inside the caller's open transaction. */
  private long timeOneCreate(int targets) {
    Inject inject = inject(targets);
    long start = System.nanoTime();
    tx.executeWithoutResult(status -> ingestionService.persistExecution(ingestionService.getAttackPathExecution(inject, step(), "")));
    return System.nanoTime() - start;
  }

  private double throughputAt(int concurrency) throws Exception {
    try (ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
      List<Callable<Void>> work = new ArrayList<>();
      for (int i = 0; i < concurrency * RUNS; i++) {
        work.add(
            () -> {
              timeOneCreate(10);
              return null;
            });
      }
      long start = System.nanoTime();
      pool.invokeAll(work);
      double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
      return work.size() / seconds;
    }
  }

  private static long percentile(List<Long> sorted, double q) {
    return sorted.get(Math.min(sorted.size() - 1, (int) Math.floor(q * sorted.size())));
  }

  private static double ms(long nanos) {
    return nanos / 1_000_000.0;
  }

  /** An inject fanning out to {@code targets} endpoints, one agent each: N rows per create. */
  private Inject inject(int targets) {
    List<io.openaev.database.model.Asset> assets = new ArrayList<>();
    for (int i = 0; i < targets; i++) {
      Agent agent = new Agent();
      agent.setId("bench-agent-" + i);
      agent.setPrivilege(Agent.PRIVILEGE.admin);

      Endpoint endpoint = new Endpoint();
      endpoint.setId("bench-endpoint-" + i);
      endpoint.setHostname("bench-host-" + i);
      endpoint.setIps(new String[] {"10.0.0." + (i % 250)});
      endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
      endpoint.setAgents(List.of(agent));
      assets.add(endpoint);
    }

    Injector injector = new Injector();
    injector.setName("OpenAEV Implant");
    injector.setType("openaev_implant");

    Exercise exercise = new Exercise();
    exercise.setId(SIM);

    Inject inject = new Inject();
    // Fresh id per iteration, so the deterministic row id differs and every create truly inserts.
    inject.setId("bench-inject-" + UUID.randomUUID());
    inject.setExercise(exercise);
    inject.setInjector(injector);
    inject.setAssets(assets);
    inject.setTenant(tenant);
    return inject;
  }

  private InjectorContract contract() {
    Command command = new Command();
    command.setId("bench-cmd");
    command.setName("crackmapexec");
    command.setContent("cme --local-auth");

    InjectorContract contract = new InjectorContract();
    contract.setNeedsExecutor(true);
    contract.setPayload(command);
    contract.setExternalId("bench-contract");
    return contract;
  }

  private Step step() {
    Step template = new Step();
    template.setId("bench-template");
    Step step = new Step();
    step.setId("bench-step");
    step.setStepTemplate(template);
    return step;
  }
}
