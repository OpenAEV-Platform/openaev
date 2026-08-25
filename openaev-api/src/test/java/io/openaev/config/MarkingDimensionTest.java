package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the marking dimension in isolation: the anti-join predicate it emits, how it composes with
 * the tenant dimension inside the single inspector, and the rollout knob (inert until a table is
 * allowlisted, fail-fast on an unknown one).
 *
 * <p>The semantics of the predicate against real rows are proven separately by {@code
 * MarkingRewriteHypothesisTest}; this test pins its shape.
 */
@DisplayName("MarkingDimension")
class MarkingDimensionTest {

  private static final MarkedTable DOCS =
      new MarkedTable("documents", "doc_id", "documents_markings", "doc_id");

  private static final MarkedTables ACTIVE = new MarkedTables(Map.of("documents", DOCS));

  private static String flatten(String sql) {
    return sql.replaceAll("\\s+", " ").trim();
  }

  @Nested
  @DisplayName("predicate")
  class Predicate {

    private final MarkingDimension dimension = new MarkingDimension(ACTIVE);

    @Test
    @DisplayName("is an anti-join correlating the marked table with its join table")
    void emitsAntiJoin() {
      assertEquals(
          "NOT EXISTS (SELECT 1 FROM documents_markings d_mk"
              + " WHERE d_mk.doc_id = d.doc_id"
              + " AND is_marking_missing(d_mk.marking_id))",
          dimension.readPredicate("documents", "d"));
    }

    @Test
    @DisplayName("uses the same predicate for reads and writes")
    void writeMatchesRead() {
      assertEquals(
          dimension.readPredicate("documents", "d"), dimension.writePredicate("documents", "d"));
    }

    @Test
    @DisplayName("derives the join alias from the table alias so a self-join stays unambiguous")
    void joinAliasFollowsTheTableAlias() {
      assertTrue(dimension.readPredicate("documents", "d1").contains("documents_markings d1_mk"));
      assertTrue(dimension.readPredicate("documents", "d2").contains("documents_markings d2_mk"));
    }

    @Test
    @DisplayName("declares no write attribution: the marking of a row is not set by this rewrite")
    void noWriteAttribution() {
      assertEquals(null, dimension.writeAttributionColumn());
    }
  }

  @Nested
  @DisplayName("inside the inspector")
  class InsideTheInspector {

    private final TenantDimension tenant =
        new TenantDimension(new TenantTables(Set.of("documents"), Set.of()));

    @Test
    @DisplayName("an empty allowlist leaves the emitted SQL byte-identical to tenant-only")
    void inertWhenNoTableIsActive() {
      ScopeStatementInspector withMarking =
          new ScopeStatementInspector(List.of(tenant, new MarkingDimension(MarkedTables.EMPTY)));
      ScopeStatementInspector tenantOnly = new ScopeStatementInspector(List.of(tenant));
      for (String sql :
          List.of(
              "SELECT * FROM documents d WHERE d.id = ?",
              "UPDATE documents SET name = ? WHERE id = ?",
              "DELETE FROM documents WHERE id = ?")) {
        assertEquals(tenantOnly.inspect(sql), withMarking.inspect(sql), sql);
      }
    }

    @Test
    @DisplayName("ANDs the anti-join onto the tenant predicate on a table both dimensions cover")
    void composesWithTenant() {
      ScopeStatementInspector inspector =
          new ScopeStatementInspector(List.of(tenant, new MarkingDimension(ACTIVE)));
      String out = flatten(inspector.inspect("SELECT * FROM documents d WHERE d.id = ?"));
      assertTrue(
          out.contains(
              "WHERE can_access_tenant(d.tenant_id)"
                  + " AND NOT EXISTS (SELECT 1 FROM documents_markings d_mk"
                  + " WHERE d_mk.doc_id = d.doc_id"
                  + " AND is_marking_missing(d_mk.marking_id))"),
          out);
    }

    @Test
    @DisplayName("guards the WHERE of an UPDATE with the anti-join too")
    void guardsUpdate() {
      ScopeStatementInspector inspector =
          new ScopeStatementInspector(List.of(new MarkingDimension(ACTIVE)));
      String out = flatten(inspector.inspect("UPDATE documents SET name = ? WHERE doc_id = ?"));
      assertTrue(out.contains("NOT EXISTS (SELECT 1 FROM documents_markings documents_mk"), out);
      assertTrue(out.contains("documents_mk.doc_id = documents.doc_id"), out);
    }

    @Test
    @DisplayName("filters a marked table reached through a join")
    void filtersJoinedTable() {
      ScopeStatementInspector inspector =
          new ScopeStatementInspector(List.of(new MarkingDimension(ACTIVE)));
      String out =
          flatten(inspector.inspect("SELECT * FROM other o JOIN documents d ON d.doc_id = o.ref"));
      assertTrue(out.contains("JOIN (SELECT * FROM documents d WHERE NOT EXISTS"), out);
    }
  }

  @Nested
  @DisplayName("activation allowlist")
  class Allowlist {

    @Test
    @DisplayName("keeps only the allowlisted tables")
    void keepsOnlyAllowlisted() {
      MarkedTables derived =
          new MarkedTables(
              Map.of(
                  "documents",
                  DOCS,
                  "assets",
                  new MarkedTable("assets", "asset_id", "assets_markings", "asset_id")));
      MarkedTables active = derived.restrictTo(List.of("assets"));
      assertEquals(Set.of("assets"), active.tableNames());
    }

    @Test
    @DisplayName("an empty allowlist activates nothing")
    void emptyAllowlistIsInert() {
      assertTrue(
          new MarkedTables(Map.of("documents", DOCS)).restrictTo(List.of()).tableNames().isEmpty());
    }

    @Test
    @DisplayName("a table with no join table fails fast rather than staying silently unprotected")
    void unknownTableFailsFast() {
      MarkedTables derived = new MarkedTables(Map.of("documents", DOCS));
      IllegalArgumentException error =
          assertThrows(IllegalArgumentException.class, () -> derived.restrictTo(List.of("assets")));
      assertTrue(error.getMessage().contains("assets"), error.getMessage());
    }

    @Test
    @DisplayName("matches table names case-insensitively")
    void caseInsensitive() {
      MarkedTables active =
          new MarkedTables(Map.of("documents", DOCS)).restrictTo(List.of("Documents"));
      assertEquals(DOCS, active.get("DOCUMENTS"));
    }
  }
}
