package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
 * (fail-closed).
 *
 * <p>The leak oracle is textual and deliberately independent of JSQLParser's AST traversal, so a
 * construct the rewriter's finder fails to visit is caught here rather than leaking silently. It is
 * checked <b>per table</b> on the rewritten SQL: every {@code FROM}/{@code JOIN} reference to a
 * tenant table must be a {@code can_access_tenant} wrapper, so a single unwrapped reference breaks
 * the count even when other tables in the same statement are wrapped.
 *
 * <p>Scope: the corpus is the per-entity {@code SELECT}, which exhaustively exercises read SQL
 * (inheritance joins, eager associations) across the whole model. Write SQL (UPDATE/DELETE/INSERT)
 * is covered by the inspector's unit tests; capturing it from a full run is a later enrichment.
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
    Map<String, TablePatterns> tenantTables = new LinkedHashMap<>();
    Stream.concat(tables.strict().stream(), tables.dualScope().stream())
        .sorted()
        .forEach(t -> tenantTables.put(t, TablePatterns.forTable(t)));

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
      Map<String, TablePatterns> mentioned = new LinkedHashMap<>();
      tenantTables.forEach(
          (table, patterns) -> {
            if (patterns.mention().matcher(sql).find()) {
              mentioned.put(table, patterns);
            }
          });
      if (mentioned.isEmpty()) {
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
      List<String> unguarded = new ArrayList<>();
      mentioned.forEach(
          (table, patterns) -> {
            int refs = count(rewritten, patterns.ref());
            int wraps = count(rewritten, patterns.wrap());
            if (refs != wraps) {
              unguarded.add(table + "(refs=" + refs + ", wrapped=" + wraps + ")");
            }
          });
      if (unguarded.isEmpty()) {
        wrapped++;
      } else {
        leaks.add(unguarded + " :: " + truncate(rewritten));
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

  @Test
  @DisplayName("the per-table oracle catches an unwrapped reference (it can fail)")
  void oracleDetectsAnUnwrappedReference() {
    TablePatterns p = TablePatterns.forTable("documents");

    String clean =
        "SELECT * FROM (SELECT * FROM documents d1_0 WHERE can_access_tenant(d1_0.tenant_id))"
            + " AS d1_0";
    assertEquals(
        count(clean, p.ref()), count(clean, p.wrap()), "a fully wrapped read must balance");

    // one wrapped reference plus a second join left unwrapped — the leak the gate must catch
    String leaky = clean + " JOIN documents x1_0 ON d1_0.id = x1_0.parent";
    assertTrue(
        count(leaky, p.ref()) > count(leaky, p.wrap()),
        "an unwrapped reference must leave more refs than wrappers");
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

  private static int count(String haystack, Pattern pattern) {
    Matcher matcher = pattern.matcher(haystack);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private static String truncate(String sql) {
    return sql.length() <= 200 ? sql : sql.substring(0, 200) + "…";
  }

  /**
   * Textual matchers for one tenant table, all independent of the SQL parser: {@code mention}
   * detects the table name as a word; {@code ref} matches a {@code FROM}/{@code JOIN} to it; {@code
   * wrap} matches the {@code can_access_tenant} wrapper the rewriter must produce. A read is safe
   * for this table when every {@code ref} is a {@code wrap}.
   */
  private record TablePatterns(Pattern mention, Pattern ref, Pattern wrap) {
    static TablePatterns forTable(String table) {
      String name = Pattern.quote(table);
      return new TablePatterns(
          Pattern.compile("(?i)(?<![a-z0-9_])" + name + "(?![a-z0-9_])"),
          Pattern.compile("(?i)\\b(?:from|join)\\s+\"?" + name + "\"?(?![a-z0-9_])"),
          Pattern.compile(
              "(?i)\\bfrom\\s+\"?" + name + "\"?\\s+\\S+\\s+where\\s+can_access_tenant\\("));
    }
  }
}
