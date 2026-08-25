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
 * Covers the marking dimension in isolation: the containment predicate it emits, how it composes
 * with the tenant dimension inside the single inspector, and the rollout knob (inert until a table
 * is allowlisted, fail-fast on an unknown one).
 *
 * <p>The semantics of the predicate against real rows are proven separately by {@code
 * MarkingRewriteHypothesisTest}; this test pins its shape.
 */
@DisplayName("MarkingDimension")
class MarkingDimensionTest {

  private static final MarkedTable DOCS = new MarkedTable("documents");

  private static final MarkedTables ACTIVE = new MarkedTables(Map.of("documents", DOCS));

  private static String flatten(String sql) {
    return sql.replaceAll("\\s+", " ").trim();
  }

  @Nested
  @DisplayName("predicate")
  class Predicate {

    private final MarkingDimension dimension = new MarkingDimension(ACTIVE);

    @Test
    @DisplayName("is a local column test on the alias, like the tenant one")
    void emitsContainmentTest() {
      assertEquals(
          "is_marking_set_allowed(d.marking_ids)", dimension.readPredicate("documents", "d"));
    }

    @Test
    @DisplayName("uses the same predicate for reads and writes")
    void writeMatchesRead() {
      assertEquals(
          dimension.readPredicate("documents", "d"), dimension.writePredicate("documents", "d"));
    }

    @Test
    @DisplayName("qualifies the column with the alias so a self-join stays unambiguous")
    void columnFollowsTheTableAlias() {
      assertEquals(
          "is_marking_set_allowed(d1.marking_ids)", dimension.readPredicate("documents", "d1"));
      assertEquals(
          "is_marking_set_allowed(d2.marking_ids)", dimension.readPredicate("documents", "d2"));
    }

    @Test
    @DisplayName("never mentions a primary key, so composite-key tables need no special case")
    void ignoresThePrimaryKey() {
      MarkingDimension links =
          new MarkingDimension(new MarkedTables(Map.of("links", new MarkedTable("links"))));
      assertEquals("is_marking_set_allowed(l.marking_ids)", links.readPredicate("links", "l"));
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
    @DisplayName("ANDs the containment test onto the tenant predicate on a table both cover")
    void composesWithTenant() {
      ScopeStatementInspector inspector =
          new ScopeStatementInspector(List.of(tenant, new MarkingDimension(ACTIVE)));
      String out = flatten(inspector.inspect("SELECT * FROM documents d WHERE d.id = ?"));
      assertTrue(
          out.contains(
              "WHERE can_access_tenant(d.tenant_id) AND is_marking_set_allowed(d.marking_ids)"),
          out);
    }

    @Test
    @DisplayName("guards the WHERE of an UPDATE too")
    void guardsUpdate() {
      ScopeStatementInspector inspector =
          new ScopeStatementInspector(List.of(new MarkingDimension(ACTIVE)));
      String out = flatten(inspector.inspect("UPDATE documents SET name = ? WHERE doc_id = ?"));
      assertTrue(out.contains("is_marking_set_allowed(documents.marking_ids)"), out);
    }

    @Test
    @DisplayName("filters a marked table reached through a join")
    void filtersJoinedTable() {
      ScopeStatementInspector inspector =
          new ScopeStatementInspector(List.of(new MarkingDimension(ACTIVE)));
      String out =
          flatten(inspector.inspect("SELECT * FROM other o JOIN documents d ON d.doc_id = o.ref"));
      assertTrue(
          out.contains(
              "JOIN (SELECT * FROM documents d WHERE is_marking_set_allowed(d.marking_ids)"),
          out);
    }
  }

  @Nested
  @DisplayName("activation allowlist")
  class Allowlist {

    @Test
    @DisplayName("keeps only the allowlisted tables")
    void keepsOnlyAllowlisted() {
      MarkedTables derived =
          new MarkedTables(Map.of("documents", DOCS, "assets", new MarkedTable("assets")));
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
    @DisplayName("a table with no marking column fails fast rather than staying unprotected")
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
