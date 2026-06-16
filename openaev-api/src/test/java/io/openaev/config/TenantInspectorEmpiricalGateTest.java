package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empirical barrier (hard prerequisite to wiring the inspector). Captures the SQL Hibernate
 * actually emits for a read of every mapped entity, then replays each statement through {@link
 * TenantStatementInspector} built from the real metamodel and asserts no read leaves a tenant table
 * unguarded — either the statement is rewritten with {@code can_access_tenant} or it is refused
 * (fail-closed). The leak check is {@link TenantSqlLeakOracle}, deliberately independent of the
 * parser whose completeness it verifies.
 *
 * <p>Scope: the corpus is the per-entity {@code SELECT}, which exhaustively exercises read SQL
 * (inheritance joins, eager associations) across the whole model. Realistic repository queries and
 * write SQL are validated on captured real SQL in {@code TenantSqlReplayMeasurementTest} (T1b).
 */
@SpringBootTest(
    properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
            + "io.openaev.config.CapturingStatementInspector")
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("Tenant inspector — empirical barrier on real emitted SQL")
class TenantInspectorEmpiricalGateTest {

  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private EntityManager entityManager;

  @Test
  @Transactional
  @DisplayName("no real read SELECT leaves a tenant table unguarded")
  void noReadLeavesTenantTableUnguarded() {
    List<Class<?>> entities =
        entityManagerFactory.getMetamodel().getEntities().stream()
            .<Class<?>>map(EntityType::getJavaType)
            .toList();
    TenantTables tables = TenantTables.fromEntities(entities);
    Set<String> tenantTables = new TreeSet<>();
    tenantTables.addAll(tables.strict());
    tenantTables.addAll(tables.dualScope());
    TenantSqlLeakOracle oracle = new TenantSqlLeakOracle(tenantTables);

    List<String> captured = captureEntityReads();

    TenantStatementInspector inspector = new TenantStatementInspector(tables);
    Set<String> distinct = new LinkedHashSet<>(captured);

    int selects = 0;
    int wrapped = 0;
    int failClosed = 0;
    List<String> leaks = new ArrayList<>();
    List<String> failClosedSamples = new ArrayList<>();

    for (String sql : distinct) {
      if (!startsWithSelect(sql)) {
        continue; // the per-entity read corpus is SELECT only; guard the read path here
      }
      if (oracle.mentioned(sql).isEmpty()) {
        continue;
      }
      selects++;
      String rewritten;
      try {
        rewritten = inspector.inspect(sql);
      } catch (TenantFilteringException e) {
        failClosed++;
        if (failClosedSamples.size() < 10) {
          failClosedSamples.add(e.getMessage() + " :: " + truncate(sql));
        }
        continue;
      }
      List<String> unwrapped = oracle.unwrappedTenantTables(rewritten);
      if (unwrapped.isEmpty()) {
        wrapped++;
      } else {
        leaks.add(unwrapped + " :: " + truncate(rewritten));
      }
    }

    System.out.println(
        "[T5-gate v2] captured="
            + captured.size()
            + " distinct="
            + distinct.size()
            + " tenant-referencing SELECTs="
            + selects
            + " (wrapped="
            + wrapped
            + ", fail-closed="
            + failClosed
            + ", leaks="
            + leaks.size()
            + ")");
    failClosedSamples.forEach(s -> System.out.println("[T5-gate v2] fail-closed: " + s));
    leaks.forEach(s -> System.out.println("[T5-gate v2] LEAK: " + s));

    assertTrue(
        selects > 0, "expected the metamodel read corpus to reference at least one tenant table");
    assertTrue(leaks.isEmpty(), "tenant tables left unguarded by the rewriter:\n" + leaks);
  }

  /** Runs a bounded read of every entity so Hibernate emits its real SELECT SQL. */
  private List<String> captureEntityReads() {
    CapturingStatementInspector.start();
    try {
      for (EntityType<?> type : entityManagerFactory.getMetamodel().getEntities()) {
        try {
          entityManager
              .createQuery("select e from " + type.getName() + " e")
              .setMaxResults(1)
              .getResultList();
        } catch (RuntimeException ignored) {
          // an entity that cannot be selected as a root still contributes nothing to the corpus
        }
      }
    } finally {
      CapturingStatementInspector.stop();
    }
    return CapturingStatementInspector.captured();
  }

  private static boolean startsWithSelect(String sql) {
    return sql.toLowerCase(Locale.ROOT).stripLeading().startsWith("select");
  }

  private static String truncate(String sql) {
    return sql.length() <= 200 ? sql : sql.substring(0, 200) + "…";
  }
}
