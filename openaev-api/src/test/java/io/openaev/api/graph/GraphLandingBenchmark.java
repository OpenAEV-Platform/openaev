package io.openaev.api.graph;

import io.openaev.IntegrationTest;
import io.openaev.api.graph.dto.GraphOutput;
import io.openaev.api.graph.dto.GraphOverviewOutput;
import io.openaev.api.graph.dto.GraphStartingPointsOutput;
import io.openaev.context.TenantContext;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Latency benchmark of the graph landing (#9447): the top exposure story, the estate overview and
 * the starting-points rail over a large estate. Not a unit test: it seeds the estate with set-based
 * SQL and times each read. Disabled by default (it needs a database and takes minutes); run it
 * explicitly:
 *
 * <pre>
 * GRAPH_LANDING_BENCHMARK=true GRAPH_LANDING_BENCHMARK_PRESET=full \
 *   mvn -pl openaev-api test -Dtest=GraphLandingBenchmark
 * </pre>
 *
 * <p>{@code PRESET} is {@code medium} (10,000 assets, 100,000 expectations) by default, or {@code
 * full} (100,000 assets, 1,000,000 expectations) for the headline numbers. 90% of the assets sit in
 * 200 static groups, the rest is the ungrouped bucket; one expectation in seven failed. Each read
 * is timed cold (the landing cache cleared and the persistence context emptied before every call)
 * and the overview also warm (served by the cache). The report gives p50 and p95 per read and
 * checks them against the p95 target of 1 second; it is printed to stdout.
 */
@Tag("benchmark")
@EnabledIfEnvironmentVariable(named = "GRAPH_LANDING_BENCHMARK", matches = "true")
@Transactional
@WithMockUser(isAdmin = true)
class GraphLandingBenchmark extends IntegrationTest {

  private static final int WARMUP = 3;
  private static final int ITERATIONS = 15;
  private static final long TARGET_P95_MS = 1_000;
  private static final int GROUPS = 200;
  private static final int SIMULATIONS = 40;
  private static final int INJECTS = 2_000;
  private static final String PREFIX = "glb-";

  @Autowired private GraphLandingService landing;
  @Autowired private GraphLandingCache cache;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private record Preset(String name, int assets, int expectations) {}

  @Test
  void benchmark() {
    Preset preset = preset();
    String tenant = TenantContext.getCurrentTenant();
    tenantHelper.attachCurrentUserToTenant(tenant);
    List<String> scope = List.of(tenant);
    StringBuilder out = new StringBuilder();
    line(out, "# Graph landing benchmark, preset=" + preset.name());

    long t0 = System.currentTimeMillis();
    seed(tenant, preset);
    execute("ANALYZE assets");
    execute("ANALYZE asset_groups_assets");
    execute("ANALYZE injects_expectations");
    line(
        out,
        "seed: %d assets, %d groups, %d simulations, %d injects, %d expectations in %d ms"
            .formatted(
                preset.assets(),
                GROUPS,
                SIMULATIONS,
                INJECTS,
                preset.expectations(),
                System.currentTimeMillis() - t0));

    line(out, "\n## Latency (warmup %d, iterations %d)".formatted(WARMUP, ITERATIONS));
    line(out, "read | p50 ms | p95 ms | max ms | target p95 < %d ms".formatted(TARGET_P95_MS));
    measure(out, "story (cold)", true, () -> landing.story(scope, GraphVisibility.ALL));
    measure(out, "overview (cold)", true, () -> landing.overview(scope, GraphVisibility.ALL));
    measure(out, "overview (cached)", false, () -> landing.overview(scope, GraphVisibility.ALL));
    measure(
        out,
        "starting points (cold)",
        true,
        () -> landing.startingPoints(scope, GraphVisibility.ALL));

    GraphOutput story = landing.story(scope, GraphVisibility.ALL);
    GraphOverviewOutput overview = landing.overview(scope, GraphVisibility.ALL);
    GraphStartingPointsOutput points = landing.startingPoints(scope, GraphVisibility.ALL);
    line(out, "\n## Payload");
    line(
        out,
        "story: %d nodes, %d edges, truncated=%s"
            .formatted(story.nodes().size(), story.edges().size(), story.truncated()));
    line(
        out,
        "overview: %d bubbles, %d links, %d assets, truncated=%s"
            .formatted(
                overview.groups().size(),
                overview.links().size(),
                overview.assetCount(),
                overview.truncated()));
    line(out, "starting points: %d entries".formatted(points.points().size()));

    System.out.println(out);
  }

  private void measure(StringBuilder out, String name, boolean cold, Supplier<?> read) {
    for (int i = 0; i < WARMUP; i++) {
      prepare(cold);
      read.get();
    }
    List<Long> samples = new ArrayList<>();
    for (int i = 0; i < ITERATIONS; i++) {
      prepare(cold);
      long start = System.nanoTime();
      read.get();
      samples.add((System.nanoTime() - start) / 1_000_000);
    }
    Collections.sort(samples);
    long p50 = percentile(samples, 50);
    long p95 = percentile(samples, 95);
    line(
        out,
        "%s | %d | %d | %d | %s"
            .formatted(
                name,
                p50,
                p95,
                samples.get(samples.size() - 1),
                p95 < TARGET_P95_MS ? "PASS" : "FAIL"));
  }

  private void prepare(boolean cold) {
    if (cold) {
      cache.clear();
    }
    entityManager.flush();
    entityManager.clear();
  }

  static long percentile(List<Long> sorted, int p) {
    int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
  }

  /** The estate, set-based: assets, static groups, simulations, injects and expectations. */
  private void seed(String tenant, Preset preset) {
    String contract = PREFIX + "contract";
    execute(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " tenant_id) VALUES ('"
            + contract
            + "', '{}', '"
            + tenant
            + "')");
    execute(
        "INSERT INTO assets (asset_id, asset_type, asset_name, asset_created_at,"
            + " asset_updated_at, tenant_id)"
            + " SELECT '"
            + PREFIX
            + "a-' || i, 'Endpoint', 'bench-host-' || i, now(), now(), '"
            + tenant
            + "' FROM generate_series(1, "
            + preset.assets()
            + ") i");
    execute(
        "INSERT INTO asset_groups (asset_group_id, asset_group_name, asset_group_created_at,"
            + " asset_group_updated_at, tenant_id)"
            + " SELECT '"
            + PREFIX
            + "g-' || g, 'bench-group-' || g, now(), now(), '"
            + tenant
            + "' FROM generate_series(0, "
            + (GROUPS - 1)
            + ") g");
    int grouped = preset.assets() * 9 / 10;
    execute(
        "INSERT INTO asset_groups_assets (asset_group_id, asset_id)"
            + " SELECT '"
            + PREFIX
            + "g-' || (i % "
            + GROUPS
            + "), '"
            + PREFIX
            + "a-' || i FROM generate_series(1, "
            + grouped
            + ") i");
    execute(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, tenant_id)"
            + " SELECT '"
            + PREFIX
            + "x-' || s, 'bench-simulation-' || s, 'FINISHED', '"
            + tenant
            + "' FROM generate_series(0, "
            + (SIMULATIONS - 1)
            + ") s");
    execute(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_exercise, inject_injector_contract, tenant_id)"
            + " SELECT '"
            + PREFIX
            + "i-' || n, 'bench-inject-' || n, false, true, 0, '"
            + PREFIX
            + "x-' || (n % "
            + SIMULATIONS
            + "), '"
            + contract
            + "', '"
            + tenant
            + "' FROM generate_series(0, "
            + (INJECTS - 1)
            + ") n");
    // Expectation j targets asset (j % assets) + 1, through its group when the asset is grouped;
    // one in seven failed (score 0 of 100).
    execute(
        "INSERT INTO injects_expectations (inject_expectation_id, inject_id,"
            + " inject_expectation_type, inject_expiration_time, asset_id, asset_group_id,"
            + " inject_expectation_score, inject_expectation_expected_score,"
            + " inject_expectation_created_at, inject_expectation_updated_at)"
            + " SELECT '"
            + PREFIX
            + "e-' || j, '"
            + PREFIX
            + "i-' || (j % "
            + INJECTS
            + "), 'PREVENTION', 3600, '"
            + PREFIX
            + "a-' || ((j % "
            + preset.assets()
            + ") + 1), CASE WHEN (j % "
            + preset.assets()
            + ") + 1 <= "
            + grouped
            + " THEN '"
            + PREFIX
            + "g-' || (((j % "
            + preset.assets()
            + ") + 1) % "
            + GROUPS
            + ") END, CASE WHEN j % 7 = 0 THEN 0 ELSE 100 END, 100, now(), now()"
            + " FROM generate_series(1, "
            + preset.expectations()
            + ") j");
    entityManager.flush();
    entityManager.clear();
  }

  private void execute(String sql) {
    entityManager.createNativeQuery(sql).executeUpdate();
  }

  private static Preset preset() {
    String name = System.getenv().getOrDefault("GRAPH_LANDING_BENCHMARK_PRESET", "medium");
    return "full".equals(name)
        ? new Preset("full", 100_000, 1_000_000)
        : new Preset("medium", 10_000, 100_000);
  }

  private static void line(StringBuilder out, String text) {
    out.append(text).append('\n');
  }
}
