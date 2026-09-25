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
 *
 * <p><b>Synthetic, not config-driven:</b> the inspector below is hand-built with a {@code
 * documents} entry hardcoded as v1 (empty {@link TenantTables}) and marking-active — it does not
 * read the real {@code Document} entity's {@code @Filter} annotation, nor {@code
 * openaev.tenant.active-tables} / {@code openaev.marking.active-tables} from {@code
 * application.properties}. It will keep passing even if {@code documents} is later migrated to
 * tenant v2, since nothing here is wired to that migration. If/when that happens, this class stops
 * describing a real table and should be re-pointed at a table that is still genuinely v1, or have
 * this note updated to say so explicitly.
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
    // The primary table is narrowed into the existing WHERE (not wrapped): the v1 condition and
    // the marking predicate are ANDed together, both apply.
    assertTrue(out.contains("WHERE (d.tenant_id = ? AND d.name = ?)"), out);
    assertTrue(out.endsWith("AND (" + MARKING_PREDICATE + ")"), out);
  }

  @Test
  @DisplayName("the wrapper projects everything, so the v1 condition still resolves tenant_id")
  void wrapperKeepsTheTenantColumnAvailable() {
    // The primary table is narrowed into the WHERE: the v1 condition and the marking predicate are
    // ANDed together in place, so the outer reference to tenant_id still resolves against the base
    // table (never wrapped away).
    String out = rewrite("SELECT d.doc_id FROM documents d WHERE d.tenant_id = ?");
    assertTrue(out.contains("WHERE (d.tenant_id = ?) AND (" + MARKING_PREDICATE + ")"), out);
  }

  @Test
  @DisplayName("a v1 dual-scope OR condition keeps its parentheses")
  void v1OrConditionKeepsItsPrecedence() {
    String out =
        rewrite(
            "SELECT d.doc_id FROM documents d"
                + " WHERE (d.tenant_id = ? OR d.tenant_id IS NULL) AND d.name = ?");
    assertTrue(
        out.endsWith(
            "WHERE ((d.tenant_id = ? OR d.tenant_id IS NULL) AND d.name = ?) AND ("
                + MARKING_PREDICATE
                + ")"),
        out);
  }

  @Test
  @DisplayName("a joined v1 table is filtered on the marked side only")
  void joinIsFilteredOnTheMarkedSideOnly() {
    String out =
        rewrite(
            "SELECT d.doc_id FROM documents d JOIN tags t ON t.doc_id = d.doc_id"
                + " WHERE d.tenant_id = ? AND t.label = ?");
    // documents is the primary table, narrowed into the WHERE; tags is not covered by any
    // dimension, so the join stays untouched.
    assertTrue(
        out.contains("WHERE (d.tenant_id = ? AND t.label = ?) AND (" + MARKING_PREDICATE + ")"),
        out);
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
