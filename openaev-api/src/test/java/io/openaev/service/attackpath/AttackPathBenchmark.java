package io.openaev.service.attackpath;

import io.openaev.IntegrationTest;
import io.openaev.database.model.attackpath.projection.AttackPathExecutionRow;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.dto.AttackPathSeedResultDTO;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.Session;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feasibility benchmark for the attack-path rebuild (issue 6647). Not a unit test: it seeds a large
 * dataset and times {@link AttackPathGraphService#buildGraph} per simulation-size bucket. Disabled
 * by default (it takes minutes and needs a warm database); run it explicitly:
 *
 * <pre>
 * ATTACKPATH_BENCHMARK=true ATTACKPATH_BENCHMARK_PRESET=full \
 *   mvn -pl openaev-api test -Dtest=AttackPathBenchmark
 * </pre>
 *
 * <p>The env vars gate it (they reach the forked test JVM reliably). {@code PRESET} is {@code
 * medium} (~0.5M, one outlier) by default for a quick check, or {@code full} (~5M, the
 * ~100k/300k/500k outliers) for the headline numbers. It reports, per bucket: buildGraph p50/p95,
 * the SQL-vs-assembly split (the two reads timed apart from the in-memory pass), and the peak heap
 * of the largest simulations; the full-vs-collapsed rebuild on the outliers (latency, heap and the
 * collapsed reads-vs-assembly split), with an {@code EXPLAIN} of the collapsed endpoint GROUP BY
 * and a check that an endpoint expand reads only its one endpoint; plus the ORM-overhead A/B (JPQL
 * projection vs a native query through Hibernate, both under the inspector) and an {@code EXPLAIN}
 * of the read. It prints the report to stdout and also writes it to {@code
 * attackpath-benchmark-latest.txt} in the JVM temp dir (override with {@code
 * -Dattackpath.benchmark.report=<path>}).
 */
@Tag("benchmark")
@EnabledIfEnvironmentVariable(named = "ATTACKPATH_BENCHMARK", matches = "true")
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=",
      "openaev.tenant.active-tables=attackpath_execution,attackpath_finding"
    })
class AttackPathBenchmark extends IntegrationTest {

  private static final long SEED = 20_260_710L;
  private static final int WARMUP = 3;
  private static final int ITERATIONS = 12;
  // The full rebuild of an outlier is seconds and gigabytes of heap, so its comparison uses fewer
  // iterations than the light reads above.
  private static final int FULL_COLLAPSE_ITERATIONS = 6;
  private static final int TYPICAL_SAMPLES = 3;
  // Report path: overridable with -Dattackpath.benchmark.report=..., else the JVM temp dir, so the
  // run is reproducible on any machine (no hardcoded workspace path).
  private static final Path REPORT = resolveReportPath();

  private static Path resolveReportPath() {
    String override = System.getProperty("attackpath.benchmark.report");
    return override != null
        ? Path.of(override)
        : Path.of(System.getProperty("java.io.tmpdir"), "attackpath-benchmark-latest.txt");
  }

  @Autowired private AttackPathSeedService seedService;
  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;

  @Test
  void benchmark() throws Exception {
    AttackPathSeedParams params = preset();
    StringBuilder out = new StringBuilder();
    line(out, "# Attack path rebuild benchmark — preset=" + presetName());

    long t0 = System.currentTimeMillis();
    AttackPathSeedResultDTO seeded = seedService.generate(params);
    line(
        out,
        "seed: %d simulations, %d executions, %d findings in %d ms"
            .formatted(
                seeded.simulations(),
                seeded.executions(),
                seeded.findings(),
                System.currentTimeMillis() - t0));

    analyze();
    Map<String, Long> sizeBySim = simulationSizes();
    List<String> targets = targets(params, sizeBySim);

    line(
        out,
        "\n## buildGraph latency per bucket (warmup %d, iterations %d)"
            .formatted(WARMUP, ITERATIONS));
    line(
        out, "(reads = the two JPQL reads incl. Hibernate fetch; the pure Postgres time is in the");
    line(out, " EXPLAIN below. assembly = total - reads = the in-memory pass.)");
    line(
        out, "sim | executions | total p50/p95 ms | reads p50 ms | assembly p50 ms | peak heap MB");
    for (String sim : targets) {
      measureBucket(out, sim, sizeBySim.getOrDefault(sim, 0L), params);
    }

    fullVsCollapsed(out, params, targets, sizeBySim);
    ormOverhead(out, params, targets, sizeBySim);
    heavyColumnRead(out, params, targets, sizeBySim);
    explain(out, params, targets, sizeBySim);
    collapsedPlans(out, params, targets, sizeBySim);
    indexOnOff(out, params, targets, sizeBySim);

    line(out, "\n## Notes");
    line(
        out,
        "- Volume/cache: the index isolates the simulation, but at a large total the wide-row table");
    line(
        out,
        "  can exceed cache and the heap fetch goes to disk; compare the same-size sim across the");
    line(
        out,
        "  medium and full presets to see it (see results.md). Not volume-independent at scale.");
    line(
        out,
        "- Date-partition retention (DETACH/DROP) is demonstrated by AttackPathPartitioningDemoTest.");

    // Print first so the numbers survive even if the file write fails; then persist the report.
    System.out.println(out);
    writeReport(out.toString());
  }

  private static void writeReport(String content) {
    try {
      if (REPORT.getParent() != null) {
        Files.createDirectories(REPORT.getParent());
      }
      Files.writeString(REPORT, content);
      System.out.println("benchmark report written to " + REPORT);
    } catch (IOException e) {
      System.out.println(
          "benchmark report not written (" + e.getMessage() + "); output above stands");
    }
  }

  private void measureBucket(
      StringBuilder out, String sim, long size, AttackPathSeedParams params) {
    setScope(tenantOf(sim, params));
    for (int i = 0; i < WARMUP; i++) {
      graphService.buildGraph(sim);
    }
    List<Long> totals = new ArrayList<>();
    List<Long> reads = new ArrayList<>();
    for (int i = 0; i < ITERATIONS; i++) {
      long r0 = System.nanoTime();
      executionRepository.findGraphRows(sim);
      findingRepository.findGraphRows(sim);
      reads.add(System.nanoTime() - r0);

      long g0 = System.nanoTime();
      graphService.buildGraph(sim);
      totals.add(System.nanoTime() - g0);
    }
    long peakBytes = peakHeapDuring(() -> graphService.buildGraph(sim));
    long totalP50 = percentile(totals, 0.50);
    line(
        out,
        "%s | %d | %.0f / %.0f | %.0f | %.0f | %d"
            .formatted(
                sim.substring(sim.lastIndexOf("sim-")),
                size,
                ms(totalP50),
                ms(percentile(totals, 0.95)),
                ms(percentile(reads, 0.50)),
                Math.max(0, ms(totalP50) - ms(percentile(reads, 0.50))),
                peakBytes / (1024 * 1024)));
  }

  /**
   * The large-simulation lever (ADR-003): the full rebuild (two flat reads plus the in-memory pass
   * that materializes every node and edge) against the collapsed rebuild (four DB aggregations, no
   * per-row materialization) on the same simulation. Measured on the outliers, where the choice
   * actually matters, with the collapsed total split into its aggregation reads vs the assembly.
   */
  private void fullVsCollapsed(
      StringBuilder out,
      AttackPathSeedParams params,
      List<String> targets,
      Map<String, Long> sizes) {
    line(out, "\n## Full vs collapsed rebuild — the large-simulation lever (ADR-003)");
    line(
        out,
        "(full forces the two-read + in-memory assembly; collapsed forces the four aggregations. A");
    line(
        out,
        " simulation above the collapse threshold is served collapsed, so collapsed is that path.)");
    line(
        out,
        "sim | executions | full p50/p95 ms | collapsed p50/p95 ms | speedup | full heap MB |"
            + " collapsed heap MB | collapsed reads/assembly p50 ms");
    for (String sim : outliers(params, targets)) {
      setScope(tenantOf(sim, params));
      for (int i = 0; i < WARMUP; i++) {
        graphService.buildGraph(sim, "full");
        graphService.buildGraph(sim, "collapsed");
      }
      List<Long> full = new ArrayList<>();
      List<Long> collapsed = new ArrayList<>();
      List<Long> collapsedReads = new ArrayList<>();
      for (int i = 0; i < FULL_COLLAPSE_ITERATIONS; i++) {
        long f0 = System.nanoTime();
        graphService.buildGraph(sim, "full");
        full.add(System.nanoTime() - f0);

        long r0 = System.nanoTime();
        executionRepository.findEndpointGroups(sim);
        executionRepository.findEdgeGroups(sim);
        findingRepository.findTypeCounts(sim);
        findingRepository.findEndpointTypeCounts(sim);
        collapsedReads.add(System.nanoTime() - r0);

        long c0 = System.nanoTime();
        graphService.buildGraph(sim, "collapsed");
        collapsed.add(System.nanoTime() - c0);
      }
      long fullHeap = peakHeapDuring(() -> graphService.buildGraph(sim, "full"));
      long collapsedHeap = peakHeapDuring(() -> graphService.buildGraph(sim, "collapsed"));
      double fullP50 = ms(percentile(full, 0.50));
      double collP50 = ms(percentile(collapsed, 0.50));
      double readsP50 = ms(percentile(collapsedReads, 0.50));
      line(
          out,
          "%s | %d | %.0f / %.0f | %.0f / %.0f | %.1fx | %d | %d | %.0f / %.0f"
              .formatted(
                  sim.substring(sim.lastIndexOf("sim-")),
                  sizes.getOrDefault(sim, 0L),
                  fullP50,
                  ms(percentile(full, 0.95)),
                  collP50,
                  ms(percentile(collapsed, 0.95)),
                  collP50 > 0 ? fullP50 / collP50 : 0,
                  fullHeap / (1024 * 1024),
                  collapsedHeap / (1024 * 1024),
                  readsP50,
                  Math.max(0, collP50 - readsP50)));
    }
  }

  /**
   * The collapsed reads are single indexed aggregations and an endpoint expand touches only one
   * endpoint. EXPLAIN the endpoint GROUP BY of the largest simulation, then EXPLAIN the single
   * endpoint's relations read and show it returns a small bounded slice, not the whole simulation.
   */
  private void collapsedPlans(
      StringBuilder out,
      AttackPathSeedParams params,
      List<String> targets,
      Map<String, Long> sizes) {
    String sim = targets.get(targets.size() - 1);
    setScope(tenantOf(sim, params));
    long total = sizes.getOrDefault(sim, 0L);

    List<String> groupPlan =
        rawRows(
            "EXPLAIN (ANALYZE, BUFFERS) SELECT attackpath_execution_target_key, max(attackpath_execution_target_asset_id),"
                + " max(attackpath_execution_target_hostname), max(attackpath_execution_target_ip), max(attackpath_execution_target_platform), max(attackpath_execution_executed_at),"
                + " sum(case when attackpath_execution_prevention_status <> 'Prevented' and attackpath_execution_detection_status <> 'Detected'"
                + " then 1 else 0 end),"
                + " sum(case when attackpath_execution_prevention_status <> 'Prevented' and attackpath_execution_detection_status = 'Detected'"
                + " then 1 else 0 end)"
                + " FROM attackpath_execution WHERE attackpath_execution_simulation_id = '"
                + sim
                + "' AND can_access_tenant(tenant_id) GROUP BY attackpath_execution_target_key");
    line(
        out,
        "\n## EXPLAIN on the collapsed endpoint GROUP BY of the largest simulation (%d rows)"
            .formatted(total));
    groupPlan.forEach(l -> line(out, "  " + l));

    String endpointKey =
        rawRows(
                "SELECT attackpath_execution_target_key FROM attackpath_execution WHERE attackpath_execution_simulation_id = '"
                    + sim
                    + "' AND can_access_tenant(tenant_id) LIMIT 1")
            .get(0);
    long endpointRows = executionRepository.findByTarget(sim, endpointKey).size();
    List<String> expandPlan =
        rawRows(
            "EXPLAIN (ANALYZE, BUFFERS) SELECT attackpath_execution_id FROM attackpath_execution WHERE attackpath_execution_simulation_id = '"
                + sim
                + "' AND target_key = '"
                + endpointKey
                + "' AND can_access_tenant(tenant_id)");
    boolean indexScan =
        expandPlan.stream()
            .anyMatch(l -> l.contains("Index") && l.contains("idx_ap_exec_sim_targetkey"));
    line(
        out,
        "\n## Endpoint expand touches one endpoint, not the simulation (target_key=%s)"
            .formatted(endpointKey));
    line(
        out,
        "expand read returns %d of %d executions (%.2f%%); index scan on idx_ap_exec_sim_targetkey: %s"
            .formatted(
                endpointRows, total, total > 0 ? 100.0 * endpointRows / total : 0.0, indexScan));
    expandPlan.forEach(l -> line(out, "  " + l));
  }

  /** The outlier simulations (the large ones), where full vs collapsed actually diverges. */
  private List<String> outliers(AttackPathSeedParams params, List<String> targets) {
    int count = params.outlierSizes().size();
    if (count == 0) {
      return List.of(targets.get(targets.size() - 1));
    }
    return new ArrayList<>(targets.subList(targets.size() - count, targets.size()));
  }

  private void ormOverhead(
      StringBuilder out,
      AttackPathSeedParams params,
      List<String> targets,
      Map<String, Long> sizes) {
    line(
        out,
        "\n## ORM overhead — JPQL projection vs native query through Hibernate (both under the inspector)");
    line(out, "sim | executions | JPQL p50 ms | native p50 ms | delta ms");
    for (String sim : List.of(targets.get(0), targets.get(targets.size() - 1))) {
      setScope(tenantOf(sim, params));
      for (int i = 0; i < WARMUP; i++) {
        executionRepository.findGraphRows(sim);
        nativeExecutionRead(sim);
      }
      List<Long> jpql = new ArrayList<>();
      List<Long> nat = new ArrayList<>();
      for (int i = 0; i < ITERATIONS; i++) {
        long j0 = System.nanoTime();
        List<AttackPathExecutionRow> rows = executionRepository.findGraphRows(sim);
        jpql.add(System.nanoTime() - j0);
        long n0 = System.nanoTime();
        List<?> nrows = nativeExecutionRead(sim);
        nat.add(System.nanoTime() - n0);
        if (rows.size() != nrows.size()) {
          throw new IllegalStateException("JPQL and native row counts differ for " + sim);
        }
      }
      double jp = ms(percentile(jpql, 0.50));
      double np = ms(percentile(nat, 0.50));
      line(
          out,
          "%s | %d | %.1f | %.1f | %.1f"
              .formatted(
                  sim.substring(sim.lastIndexOf("sim-")),
                  sizes.getOrDefault(sim, 0L),
                  jp,
                  np,
                  jp - np));
    }
  }

  private void explain(
      StringBuilder out,
      AttackPathSeedParams params,
      List<String> targets,
      Map<String, Long> sizes) {
    String sim = targets.get(targets.size() - 1);
    setScope(tenantOf(sim, params));
    List<String> plan =
        rawRows(
            "EXPLAIN (ANALYZE, BUFFERS) SELECT attackpath_execution_id, attackpath_execution_source_kind, attackpath_execution_target_key, attackpath_execution_executed_at"
                + " FROM attackpath_execution"
                + " WHERE attackpath_execution_simulation_id = '"
                + sim
                + "' AND can_access_tenant(tenant_id)");
    line(
        out,
        "\n## EXPLAIN (ANALYZE, BUFFERS) on the execution read of the largest simulation (%d rows)"
            .formatted(sizes.getOrDefault(sim, 0L)));
    boolean indexScan =
        plan.stream().anyMatch(l -> l.contains("Index") && l.contains("idx_ap_exec_sim_targetkey"));
    boolean recursive = plan.stream().anyMatch(l -> l.toLowerCase().contains("recursive"));
    plan.forEach(l -> line(out, "  " + l));
    line(
        out,
        "index scan on idx_ap_exec_sim_targetkey: "
            + indexScan
            + " | recursive plan: "
            + recursive);
  }

  /**
   * The design lever: Read A projects only the short columns, never {@code command}/{@code
   * terminal_output}, so it does not detoast the heavy text a real run stores. This times Read A
   * against a {@code SELECT *} that does, on the same rows, to put a number on that choice.
   */
  private void heavyColumnRead(
      StringBuilder out,
      AttackPathSeedParams params,
      List<String> targets,
      Map<String, Long> sizes) {
    line(
        out,
        "\n## Heavy-column cost — Read A (short projection) vs SELECT * (detoasts attackpath_execution_command/attackpath_execution_terminal_output)");
    line(out, "sim | executions | Read A p50 ms | SELECT * p50 ms | detoast cost ms");
    for (String sim : List.of(targets.get(0), targets.get(targets.size() - 1))) {
      setScope(tenantOf(sim, params));
      for (int i = 0; i < WARMUP; i++) {
        executionRepository.findGraphRows(sim);
        nativeSelectStar(sim);
      }
      List<Long> shortCols = new ArrayList<>();
      List<Long> star = new ArrayList<>();
      for (int i = 0; i < ITERATIONS; i++) {
        long a0 = System.nanoTime();
        executionRepository.findGraphRows(sim);
        shortCols.add(System.nanoTime() - a0);
        long b0 = System.nanoTime();
        nativeSelectStar(sim);
        star.add(System.nanoTime() - b0);
      }
      double shortMs = ms(percentile(shortCols, 0.50));
      double starMs = ms(percentile(star, 0.50));
      line(
          out,
          "%s | %d | %.1f | %.1f | %.1f"
              .formatted(
                  sim.substring(sim.lastIndexOf("sim-")),
                  sizes.getOrDefault(sim, 0L),
                  shortMs,
                  starMs,
                  starMs - shortMs));
    }
  }

  private List<?> nativeSelectStar(String sim) {
    return entityManager
        .createNativeQuery(
            "SELECT * FROM attackpath_execution WHERE attackpath_execution_simulation_id = :sim")
        .setParameter("sim", sim)
        .getResultList();
  }

  /**
   * Drops the {@code simulation_id} index, re-measures the largest simulation's read (now a
   * sequential scan of the whole table), then recreates it — a before/after that puts a number on
   * the index. All inside the rolled-back transaction, so the schema is unchanged after the run.
   */
  private void indexOnOff(
      StringBuilder out,
      AttackPathSeedParams params,
      List<String> targets,
      Map<String, Long> sizes) {
    String sim = targets.get(targets.size() - 1);
    setScope(tenantOf(sim, params));
    for (int i = 0; i < WARMUP; i++) {
      executionRepository.findGraphRows(sim);
    }
    List<Long> withIndex = new ArrayList<>();
    for (int i = 0; i < ITERATIONS; i++) {
      long t0 = System.nanoTime();
      executionRepository.findGraphRows(sim);
      withIndex.add(System.nanoTime() - t0);
    }

    // idx_ap_exec_sim_targetkey is the only simulation_id index (the redundant single-column
    // idx_ap_exec_sim was removed), so dropping it forces a real sequential scan. Recreate it in a
    // finally: this DDL is not reliably rolled back with the test transaction, so a failure between
    // the drop and the recreate would otherwise leave the schema without the index.
    List<String> seqPlan;
    List<Long> withoutIndex = new ArrayList<>();
    ddl("DROP INDEX idx_ap_exec_sim_targetkey");
    try {
      seqPlan =
          rawRows(
              "EXPLAIN SELECT attackpath_execution_id FROM attackpath_execution"
                  + " WHERE attackpath_execution_simulation_id = '"
                  + sim
                  + "' AND can_access_tenant(tenant_id)");
      for (int i = 0; i < 3; i++) {
        long t0 = System.nanoTime();
        executionRepository.findGraphRows(sim);
        withoutIndex.add(System.nanoTime() - t0);
      }
    } finally {
      ddl(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_sim_targetkey ON attackpath_execution"
              + " (simulation_id, target_key)");
    }

    boolean seqScan = seqPlan.stream().anyMatch(l -> l.contains("Seq Scan"));
    line(
        out,
        "\n## Index on/off — read of the largest simulation (%d rows)"
            .formatted(sizes.getOrDefault(sim, 0L)));
    line(
        out,
        "with idx_ap_exec_sim_targetkey p50: %.0f ms | without it (%s) p50: %.0f ms"
            .formatted(
                ms(percentile(withIndex, 0.50)),
                seqScan ? "seq scan" : "no index",
                ms(percentile(withoutIndex, 0.50))));
  }

  private void ddl(String sql) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (var statement = connection.createStatement()) {
                statement.execute(sql);
              }
            });
  }

  /** Gives the planner statistics on the freshly seeded (uncommitted) rows before measuring. */
  private void analyze() {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (var statement = connection.createStatement()) {
                statement.execute("ANALYZE attackpath_execution");
                statement.execute("ANALYZE attackpath_finding");
              }
            });
  }

  // -- helpers --

  private List<?> nativeExecutionRead(String sim) {
    return entityManager
        .createNativeQuery(
            "SELECT attackpath_execution_id, attackpath_execution_source_kind, attackpath_execution_source_asset_id, attackpath_execution_agent_id, attackpath_execution_agent_name, attackpath_execution_agent_privilege,"
                + " source_injector, target_kind, target_asset_id, target_raw_value, target_key,"
                + " target_hostname, target_ip, target_platform, payload_name, executed_at,"
                + " prevention_status, detection_status, step_template_id"
                + " FROM attackpath_execution WHERE attackpath_execution_simulation_id = :sim")
        .setParameter("sim", sim)
        .getResultList();
  }

  private long peakHeapDuring(Runnable action) {
    var memory = ManagementFactory.getMemoryMXBean();
    System.gc();
    long baseline = memory.getHeapMemoryUsage().getUsed();
    AtomicLong peak = new AtomicLong(baseline);
    AtomicBoolean running = new AtomicBoolean(true);
    Thread sampler =
        new Thread(
            () -> {
              while (running.get()) {
                peak.updateAndGet(p -> Math.max(p, memory.getHeapMemoryUsage().getUsed()));
                try {
                  Thread.sleep(2);
                } catch (InterruptedException e) {
                  return;
                }
              }
            });
    sampler.start();
    action.run();
    running.set(false);
    try {
      sampler.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return Math.max(0, peak.get() - baseline);
  }

  private void setScope(String tenantId) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", tenantId)
        .getSingleResult();
  }

  private Map<String, Long> simulationSizes() {
    Map<String, Long> sizes = new LinkedHashMap<>();
    for (String row :
        rawRows(
            "SELECT attackpath_execution_simulation_id || '=' || count(*) FROM attackpath_execution"
                + " WHERE attackpath_execution_simulation_id LIKE 'ap-seed-%' GROUP BY attackpath_execution_simulation_id")) {
      int eq = row.lastIndexOf('=');
      sizes.put(row.substring(0, eq), Long.parseLong(row.substring(eq + 1)));
    }
    return sizes;
  }

  /** The outlier simulations (there is one row per outlier size), plus a sample of typical ones. */
  private List<String> targets(AttackPathSeedParams params, Map<String, Long> sizes) {
    List<String> outliers = new ArrayList<>();
    for (int i = 0; i < params.outlierSizes().size(); i++) {
      outliers.add(simId(i));
    }
    List<String> typical = new ArrayList<>();
    for (int i = params.outlierSizes().size();
        i < params.simulations() && typical.size() < TYPICAL_SAMPLES;
        i += Math.max(1, params.simulations() / (TYPICAL_SAMPLES + 1))) {
      typical.add(simId(i));
    }
    List<String> all = new ArrayList<>(typical);
    all.addAll(outliers);
    return all;
  }

  private String simId(int index) {
    return "ap-seed-" + SEED + "-sim-" + index;
  }

  private String tenantOf(String sim, AttackPathSeedParams params) {
    int index = Integer.parseInt(sim.substring(sim.lastIndexOf("sim-") + 4));
    return "ap-seed-" + SEED + "-tenant-" + (index % params.tenants());
  }

  private List<String> rawRows(String sql) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              List<String> rows = new ArrayList<>();
              try (PreparedStatement statement = connection.prepareStatement(sql);
                  ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                  rows.add(result.getString(1));
                }
              }
              return rows;
            });
  }

  private AttackPathSeedParams preset() {
    return switch (presetName()) {
      case "full" -> AttackPathSeedParams.full(SEED);
      case "large" -> AttackPathSeedParams.large(SEED);
      default -> AttackPathSeedParams.medium(SEED);
    };
  }

  private String presetName() {
    String preset = System.getenv("ATTACKPATH_BENCHMARK_PRESET");
    return preset == null ? "medium" : preset;
  }

  private static double ms(long nanos) {
    return nanos / 1_000_000.0;
  }

  private static long percentile(List<Long> values, double p) {
    List<Long> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    int index = (int) Math.ceil(p * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
  }

  private static void line(StringBuilder out, String text) {
    out.append(text).append('\n');
  }
}
