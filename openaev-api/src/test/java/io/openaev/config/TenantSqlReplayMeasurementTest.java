package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

/**
 * T1b — replays the SQL actually emitted by a real (restricted, shape-complete) test run through
 * {@link TenantStatementInspector} and asserts that no read leaves a tenant table unguarded, using
 * the parser-independent {@link TenantSqlLeakOracle}. It is enabled only when pointed at a capture
 * file ({@code -Dtenant.sql.replay.file=<path>}), so it stays inert in normal CI; the capture is
 * produced by running tests with {@code -Dtenant.sql.capture.file=<path>} and the capturing
 * inspector registered.
 *
 * <p>It reports the fail-closed statements (the named remediation work for wiring: native CTEs,
 * {@code UPDATE ... FROM}, {@code DELETE ... USING}, …) but only the read leaks fail the test.
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@EnabledIfSystemProperty(named = "tenant.sql.replay.file", matches = ".+")
@DisplayName("Tenant inspector — replay of captured real SQL (T1b)")
class TenantSqlReplayMeasurementTest {

  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  @DisplayName("no read in the captured real corpus leaves a tenant table unguarded")
  void noCapturedReadLeaks() throws IOException {
    Path file = Path.of(System.getProperty("tenant.sql.replay.file"));
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    Set<String> distinct = new LinkedHashSet<>(lines);
    distinct.removeIf(String::isBlank);

    List<Class<?>> entities =
        entityManagerFactory.getMetamodel().getEntities().stream()
            .<Class<?>>map(EntityType::getJavaType)
            .toList();
    TenantTables tables = TenantTables.fromEntities(entities);
    Set<String> tenantTables = new TreeSet<>();
    tenantTables.addAll(tables.strict());
    tenantTables.addAll(tables.dualScope());
    TenantSqlLeakOracle oracle = new TenantSqlLeakOracle(tenantTables);
    TenantStatementInspector inspector = new TenantStatementInspector(tables);

    Map<String, Integer> byKind = new TreeMap<>();
    Map<String, Integer> tenantByKind = new TreeMap<>();
    Map<String, Integer> wrappedByKind = new TreeMap<>();
    Map<String, Integer> failClosedByKind = new TreeMap<>();
    Map<String, String> failClosedSamples = new TreeMap<>();
    List<String> leaks = new ArrayList<>();

    for (String sql : distinct) {
      String kind = kindOf(sql);
      byKind.merge(kind, 1, Integer::sum);
      if (oracle.mentioned(sql).isEmpty()) {
        continue;
      }
      tenantByKind.merge(kind, 1, Integer::sum);

      String rewritten;
      try {
        rewritten = inspector.inspect(sql);
      } catch (TenantFilteringException e) {
        failClosedByKind.merge(kind, 1, Integer::sum);
        failClosedSamples.putIfAbsent(e.getMessage(), truncate(sql));
        continue;
      }

      List<String> unwrapped = oracle.unwrappedTenantTables(rewritten);
      boolean writeUnguarded =
          (kind.equals("update") || kind.equals("delete"))
              && !rewritten.toLowerCase(Locale.ROOT).contains("can_access_tenant(");
      if (unwrapped.isEmpty() && !writeUnguarded) {
        wrappedByKind.merge(kind, 1, Integer::sum);
      } else {
        leaks.add(
            kind
                + " "
                + (writeUnguarded ? "[write target unguarded]" : unwrapped)
                + " :: "
                + truncate(rewritten));
      }
    }

    System.out.println("[T1b] distinct statements by kind: " + byKind);
    System.out.println("[T1b] tenant-referencing by kind:  " + tenantByKind);
    System.out.println("[T1b] guarded by kind:             " + wrappedByKind);
    System.out.println("[T1b] fail-closed by kind:         " + failClosedByKind);
    System.out.println("[T1b] --- fail-closed remediation list (named) ---");
    failClosedSamples.forEach((msg, sample) -> System.out.println("[T1b] FAIL-CLOSED: " + msg));
    failClosedSamples.forEach((msg, sample) -> System.out.println("[T1b]   e.g. " + sample));
    leaks.forEach(s -> System.out.println("[T1b] LEAK: " + s));

    assertFalse(distinct.isEmpty(), "the capture file is empty — did the capture run write to it?");
    assertTrue(leaks.isEmpty(), "tenant tables left unguarded by the rewriter:\n" + leaks);
  }

  private static String kindOf(String sql) {
    String head = sql.toLowerCase(Locale.ROOT).stripLeading();
    for (String kind : List.of("select", "insert", "update", "delete", "with")) {
      if (head.startsWith(kind)) {
        return kind;
      }
    }
    return "other";
  }

  private static String truncate(String sql) {
    return sql.length() <= 240 ? sql : sql.substring(0, 240) + "…";
  }
}
