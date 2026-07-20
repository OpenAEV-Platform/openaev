package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.service.attackpath.dto.AttackPathSeedResultDTO;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Smoke coverage for the seed generator (issue 6647). It is a randomized bulk generator, so this
 * asserts the shape it must produce — fan-out, shared findings, discovered endpoints, a status mix,
 * populated run-snapshot columns, a skewed size distribution with an outlier — and its determinism,
 * not exact rows. The {@code attackpath_*} tables are activated, so this also proves the seed's
 * deliberate raw-JDBC path (which bypasses the {@code TenantStatementInspector} by design, ADR-003)
 * still writes correctly with the tables active — it sets {@code tenant_id} explicitly on every
 * row. Ground truth is read with raw JDBC on the test's own connection, so it sees the seed's rows
 * in this transaction directly, unfiltered.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH",
      "openaev.tenant.active-tables=attackpath_execution,attackpath_finding"
    })
@DisplayName("attack path seed generator produces the expected shape")
class AttackPathSeedServiceTest extends IntegrationTest {

  @Autowired private AttackPathSeedService seedService;

  @Test
  @DisplayName(
      "a smoke run generates fan-out, shared findings, discovered endpoints and an outlier")
  void smokeRunGeneratesStructuredData() {
    AttackPathSeedResultDTO result = seedService.generate(AttackPathSeedParams.smoke(42));

    assertThat(result.simulations()).isEqualTo(6);
    assertThat(result.executions()).isGreaterThan(200); // at least the one outlier simulation
    assertThat(result.findings()).isPositive();

    assertThat(
            scalar(
                "SELECT count(DISTINCT attackpath_execution_prevention_status) FROM attackpath_execution"
                    + WHERE_EXEC))
        .as("a mix of prevention statuses (green/red/orange endpoints)")
        .isGreaterThan(1);
    assertThat(
            scalar(
                "SELECT count(*) FROM (SELECT attackpath_finding_type, attackpath_finding_value"
                    + " FROM attackpath_finding"
                    + WHERE_FIND
                    + " GROUP BY attackpath_finding_type, attackpath_finding_value"
                    + " HAVING count(DISTINCT attackpath_finding_endpoint_key) > 1) shared"))
        .as("findings whose (type, value) is reused across endpoints")
        .isPositive();
    assertThat(
            scalar(
                "SELECT count(*) FROM attackpath_execution"
                    + WHERE_EXEC
                    + " AND attackpath_execution_target_asset_id IS NULL"
                    + " AND attackpath_execution_target_raw_value IS NOT NULL"))
        .as("discovered (raw) endpoints, not backed by an asset id")
        .isPositive();
    assertThat(
            scalar(
                "SELECT max(fanout) FROM (SELECT count(DISTINCT attackpath_execution_target_key) fanout"
                    + " FROM attackpath_execution"
                    + WHERE_EXEC
                    + " GROUP BY attackpath_execution_source_injector) spray"))
        .as("spray: one injector reaches many endpoints")
        .isGreaterThan(1);
    assertThat(
            scalar(
                "SELECT count(*) FROM attackpath_execution"
                    + WHERE_EXEC
                    + " AND attackpath_execution_agent_name IS NOT NULL"))
        .as("run snapshot: agent columns populated on some executions")
        .isPositive();
    assertThat(
            scalar(
                "SELECT count(*) FROM attackpath_execution"
                    + WHERE_EXEC
                    + " AND attackpath_execution_target_hostname IS NOT NULL"
                    + " AND attackpath_execution_target_ip IS NOT NULL"
                    + " AND attackpath_execution_target_platform IS NOT NULL"
                    + " AND attackpath_execution_step_template_id IS NOT NULL"))
        .as("run snapshot: endpoint and step columns populated on some executions")
        .isPositive();

    List<Long> sizes =
        counts(
            "SELECT count(*) FROM attackpath_execution"
                + WHERE_EXEC
                + " GROUP BY attackpath_execution_simulation_id");
    assertThat(max(sizes))
        .as("the skewed distribution has an outlier far above the median")
        .isGreaterThan(median(sizes) * 3);
  }

  @Test
  @DisplayName("the same seed produces the same row counts")
  void sameSeedSameCounts() {
    AttackPathSeedResultDTO first = seedService.generate(AttackPathSeedParams.smoke(7));
    AttackPathSeedResultDTO second = seedService.generate(AttackPathSeedParams.smoke(7));

    assertThat(second.simulations()).isEqualTo(first.simulations());
    assertThat(second.executions()).isEqualTo(first.executions());
    assertThat(second.findings()).isEqualTo(first.findings());
  }

  @Test
  @DisplayName("seeding under an existing tenant attributes every row to that tenant")
  void seedingUnderATenantAttributesEveryRowToIt() {
    // Flushed on purpose: the seed writes raw JDBC on the session's own connection, which only sees
    // what Hibernate has already sent. Without this the tenant would not exist yet and the rows
    // would fail their foreign key.
    Tenant target = tenantRepository.saveAndFlush(TenantFixture.getTenant("ap-seed-attribution"));

    seedService.generate(AttackPathSeedParams.smoke(11), target.getId());

    // The raw-JDBC path bypasses the inspector by design, so attribution is entirely on the seed's
    // shoulders. This is the property that makes the bypass acceptable, so it is pinned rather than
    // argued: no row may land anywhere but the requested tenant.
    // Both counts asserted positive first: a "no row under another tenant" check is trivially true
    // when the table is empty, and that would make the whole test decorative.
    assertThat(scalar("SELECT count(*) FROM attackpath_execution" + WHERE_EXEC)).isPositive();
    assertThat(scalar("SELECT count(*) FROM attackpath_finding" + WHERE_FIND)).isPositive();
    assertThat(
            scalar(
                "SELECT count(*) FROM attackpath_execution"
                    + WHERE_EXEC
                    + " AND tenant_id <> '"
                    + target.getId()
                    + "'"))
        .isZero();
    assertThat(
            scalar(
                "SELECT count(*) FROM attackpath_finding"
                    + WHERE_FIND
                    + " AND tenant_id <> '"
                    + target.getId()
                    + "'"))
        .isZero();
  }

  @Test
  @DisplayName("the raw-JDBC exemption stays insert-only, so its stated reason stays true")
  void theRawJdbcExemptionStaysInsertOnly() throws Exception {
    // The multi-tenancy runbook forbids @AllowRawJdbc on a tenant table, and this service carries
    // it. The exemption is defensible for exactly one reason: it only emits INSERT ... VALUES with
    // an explicit tenant_id, and the statement inspector adds nothing to that shape. A reason that
    // lives only in a comment rots, so it is enforced here: adding a SELECT, UPDATE or DELETE to
    // this service, or dropping tenant_id from a column list, fails the build and forces the
    // conversation rather than silently widening the bypass.
    List<Path> bypasses = rawJdbcClassesTouchingAttackPath();
    assertThat(bypasses)
        .as("only the seed may bypass the inspector on the attack path tables")
        .singleElement()
        .satisfies(p -> assertThat(p.getFileName()).hasToString("AttackPathSeedService.java"));

    for (Path bypass : bypasses) {
      assertThat(sqlLiterals(Files.readString(bypass)))
          .as("every SQL statement %s emits must be an INSERT", bypass.getFileName())
          .allSatisfy(sql -> assertThat(sql).startsWith("INSERT"));
    }
    assertThat(AttackPathSeedService.EXECUTION_COLUMNS).contains("tenant_id");
    assertThat(AttackPathSeedService.FINDING_COLUMNS).contains("tenant_id");
  }

  /**
   * Every production class that opts out of the inspector AND names an attack path table. Scanning
   * the tree rather than one known file is the point: a new sibling bypass added later must show up
   * here instead of slipping past a test that only ever looked at the seed.
   */
  private static List<Path> rawJdbcClassesTouchingAttackPath() throws Exception {
    try (Stream<Path> tree = Files.walk(Path.of("src/main/java"))) {
      return tree.filter(p -> p.toString().endsWith(".java"))
          .filter(
              p -> {
                String body = read(p);
                return body.contains("@AllowRawJdbc") && body.contains("attackpath_");
              })
          .toList();
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception e) {
      throw new IllegalStateException("cannot read " + path, e);
    }
  }

  /** The SQL statements the source builds, recognised by their leading verb. */
  private static List<String> sqlLiterals(String source) {
    Matcher matcher =
        Pattern.compile(
                "\"\\s*(INSERT|SELECT|UPDATE|DELETE|TRUNCATE|MERGE)\\b", Pattern.CASE_INSENSITIVE)
            .matcher(source);
    List<String> found = new ArrayList<>();
    while (matcher.find()) {
      found.add(matcher.group(1).toUpperCase());
    }
    assertThat(found)
        .as("the scan must find the seed's statements, or it proves nothing")
        .isNotEmpty();
    return found;
  }

  private static final String WHERE_EXEC =
      " WHERE attackpath_execution_simulation_id LIKE 'ap-seed-%'";
  private static final String WHERE_FIND =
      " WHERE attackpath_finding_simulation_id LIKE 'ap-seed-%'";

  private long scalar(String sql) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql);
                  ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
              }
            });
  }

  private List<Long> counts(String sql) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              List<Long> values = new ArrayList<>();
              try (PreparedStatement statement = connection.prepareStatement(sql);
                  ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                  values.add(rows.getLong(1));
                }
              }
              return values;
            });
  }

  private static long max(List<Long> values) {
    return values.stream().mapToLong(Long::longValue).max().orElse(0);
  }

  private static long median(List<Long> values) {
    List<Long> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    return sorted.get(sorted.size() / 2);
  }
}
