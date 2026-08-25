package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins decision Q10: a table may stay on tenant isolation <b>v1</b> (a Hibernate {@code @Filter},
 * i.e. absent from {@code openaev.tenant.active-tables}) and be marking-active at the same time.
 * This is what lets marking be activated on {@code assets} and {@code asset_groups} without waiting
 * for their tenant v2 migration.
 *
 * <p>The two mechanisms act at different layers — v1 injects its condition while Hibernate
 * <i>generates</i> the SQL, marking rewrites the finished string — so the only way they could
 * collide is through bind parameters. The marking predicate carries its clearance in a session
 * setting and adds no {@code ?}; if it ever did, the wrap being inserted in the FROM (which
 * precedes the WHERE) would shift every later placeholder and silently break positional binding.
 * Every case below therefore asserts the placeholder count is preserved.
 */
@DisplayName("Marking coexists with tenant isolation v1")
class MarkingCoexistsWithTenantV1Test {

  /** Tenant v2 inactive (empty tables) — the table is on v1 — while marking is active. */
  private final ScopeStatementInspector inspector =
      new ScopeStatementInspector(
          List.of(
              new TenantDimension(new TenantTables(Set.of(), Set.of())),
              new MarkingDimension(
                  new MarkedTables(Map.of("documents", new MarkedTable("documents"))))));

  private static long placeholders(String sql) {
    return sql.chars().filter(c -> c == '?').count();
  }

  /** Rewrites, asserts the placeholder count is untouched, and returns the flattened SQL. */
  private String rewrite(String sql) {
    String out = inspector.inspect(sql);
    assertEquals(
        placeholders(sql),
        placeholders(out),
        "the rewrite must not change the placeholder count, or positional binding breaks: " + out);
    return out.replaceAll("\\s+", " ").trim();
  }

  private static final String MARKING_PREDICATE = "is_marking_set_allowed(d.marking_ids)";

  @Test
  @DisplayName("no tenant predicate is emitted for a table that is still on v1")
  void noTenantPredicateOnAV1Table() {
    String out = rewrite("SELECT d.doc_id FROM documents d WHERE d.tenant_id = ?");
    assertTrue(!out.contains("can_access_tenant"), out);
  }

  @Test
  @DisplayName("the v1 filter condition survives verbatim next to the marking predicate")
  void v1ConditionSurvives() {
    String out =
        rewrite("SELECT d.doc_id, d.name FROM documents d WHERE d.tenant_id = ? AND d.name = ?");
    // Marking filters the wrapped source, the v1 condition stays in the outer WHERE: both apply.
    assertTrue(
        out.contains("(SELECT * FROM documents d WHERE " + MARKING_PREDICATE + ") AS d"), out);
    assertTrue(out.endsWith("WHERE d.tenant_id = ? AND d.name = ?"), out);
  }

  @Test
  @DisplayName("the wrapper projects everything, so the v1 condition still resolves tenant_id")
  void wrapperKeepsTheTenantColumnAvailable() {
    // A projected wrap (SELECT * …) is what makes an outer reference to a column the marking
    // predicate never mentions legal.
    String out = rewrite("SELECT d.doc_id FROM documents d WHERE d.tenant_id = ?");
    assertTrue(out.contains("SELECT * FROM documents d WHERE " + MARKING_PREDICATE), out);
  }

  @Test
  @DisplayName("a v1 dual-scope OR condition keeps its parentheses")
  void v1OrConditionKeepsItsPrecedence() {
    String out =
        rewrite(
            "SELECT d.doc_id FROM documents d"
                + " WHERE (d.tenant_id = ? OR d.tenant_id IS NULL) AND d.name = ?");
    assertTrue(out.endsWith("WHERE (d.tenant_id = ? OR d.tenant_id IS NULL) AND d.name = ?"), out);
  }

  @Test
  @DisplayName("a joined v1 table is filtered on the marked side only")
  void joinIsFilteredOnTheMarkedSideOnly() {
    String out =
        rewrite(
            "SELECT d.doc_id FROM documents d JOIN tags t ON t.doc_id = d.doc_id"
                + " WHERE d.tenant_id = ? AND t.label = ?");
    assertTrue(
        out.contains("(SELECT * FROM documents d WHERE " + MARKING_PREDICATE + ") AS d"), out);
    assertTrue(out.contains("JOIN tags t ON t.doc_id = d.doc_id"), out);
  }

  @Test
  @DisplayName("a bulk UPDATE is marking-guarded even though a v1 @Filter would not apply to it")
  void bulkUpdateIsGuarded() {
    // Deliberate asymmetry, recorded as Q10 consequence 2: on a v1 + marking table, marking covers
    // paths tenant v1 does not (bulk HQL updates, native queries).
    String out = rewrite("UPDATE documents SET name = ? WHERE tenant_id = ? AND doc_id = ?");
    assertTrue(out.contains("(tenant_id = ? AND doc_id = ?)"), out);
    assertTrue(out.contains("is_marking_set_allowed(documents.marking_ids)"), out);
  }

  @Test
  @DisplayName("a plain INSERT is untouched, so v1 keeps owning tenant assignment on write")
  void insertIsUntouched() {
    String sql = "INSERT INTO documents (doc_id, tenant_id, name) VALUES (?, ?, ?)";
    // MarkingDimension declares no write attribution column, so it adds no INSERT validation.
    assertEquals(sql, inspector.inspect(sql));
  }

  @Test
  @DisplayName("reading the marking column is filtered by the same predicate as the row")
  void markingColumnCannotBeReadAroundTheFilter() {
    // Retires the join-table gap: with the markings held on the marked row there is no second
    // relation to query, so "which markings does this invisible row carry?" is not expressible.
    String out = rewrite("SELECT d.marking_ids FROM documents d WHERE d.doc_id = ?");
    assertTrue(out.contains(MARKING_PREDICATE), out);
  }
}
