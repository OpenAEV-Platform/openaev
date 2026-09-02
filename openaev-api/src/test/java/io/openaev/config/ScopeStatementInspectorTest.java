package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the generic, dimension-driven rewriting {@link ScopeStatementInspector} adds on top of the
 * tenant-only behaviour pinned by {@link TenantStatementInspectorTest}: that a single dimension
 * emits exactly its own predicate (so the tenant extraction changed nothing), and that a second
 * dimension is ANDed onto the same tables rather than replacing the first.
 *
 * <p>The second dimension here is a stand-in, not the marking one: it exercises the composition
 * contract without depending on marking definitions existing.
 */
@DisplayName("ScopeStatementInspector")
class ScopeStatementInspectorTest {

  private static final TenantTables TABLES =
      new TenantTables(Set.of("documents"), Set.of("groups"));

  /** A minimal second dimension: a bare predicate on one table, no write attribution. */
  private record LabelDimension(Set<String> tables) implements ScopeDimension {
    @Override
    public String name() {
      return "label";
    }

    @Override
    public Set<String> activeTables() {
      return tables;
    }

    @Override
    public boolean covers(String table) {
      return tables.contains(table.toLowerCase());
    }

    @Override
    public String readPredicate(String table, String alias) {
      return "can_access_label(" + alias + ".label_id, true)";
    }

    @Override
    public String writePredicate(String table, String alias) {
      return "can_access_label(" + alias + ".label_id)";
    }
  }

  private static String inspect(ScopeStatementInspector inspector, String sql) {
    return inspector.inspect(sql).replaceAll("\\s+", " ").trim();
  }

  @Nested
  @DisplayName("with a single dimension")
  class SingleDimension {

    private final ScopeStatementInspector inspector =
        new ScopeStatementInspector(List.of(new TenantDimension(TABLES)));

    @Test
    @DisplayName("emits that dimension's predicate alone, with no AND wrapper")
    void singleDimensionEmitsItsPredicateVerbatim() {
      // The extraction must not change the emitted SQL: an AND-join of one element is the element.
      // documents is strict, so no allow_platform flag either.
      String out = inspect(inspector, "SELECT * FROM documents d WHERE d.id = ?");
      assertTrue(out.contains("WHERE can_access_tenant(d.tenant_id)"), out);
      assertTrue(!out.contains("AND can_access_tenant"), out);
    }

    @Test
    @DisplayName("produces the same SQL as the tenant-only inspector")
    void matchesTheTenantOnlyInspector() {
      TenantStatementInspector tenantOnly = new TenantStatementInspector(TABLES);
      for (String sql :
          List.of(
              "SELECT * FROM documents d WHERE d.id = ?",
              "SELECT * FROM documents d JOIN groups g ON g.id = d.group_id",
              "UPDATE documents SET name = ? WHERE id = ?",
              "DELETE FROM documents WHERE id = ?")) {
        assertEquals(tenantOnly.inspect(sql), inspector.inspect(sql), sql);
      }
    }
  }

  @Nested
  @DisplayName("with two dimensions")
  class TwoDimensions {

    private final ScopeStatementInspector inspector =
        new ScopeStatementInspector(
            List.of(new TenantDimension(TABLES), new LabelDimension(Set.of("documents"))));

    @Test
    @DisplayName("ANDs both predicates on a table both dimensions cover")
    void andsBothPredicatesOnACommonTable() {
      String out = inspect(inspector, "SELECT * FROM documents d WHERE d.id = ?");
      assertTrue(
          out.contains("can_access_tenant(d.tenant_id) AND can_access_label(d.label_id, true)"),
          out);
    }

    @Test
    @DisplayName("applies only the covering dimension on a table the other does not cover")
    void appliesOnlyTheCoveringDimension() {
      // groups is tenant-scoped but carries no label, so only the tenant predicate applies.
      String out = inspect(inspector, "SELECT * FROM groups g WHERE g.id = ?");
      assertTrue(out.contains("can_access_tenant(g.tenant_id, true)"), out);
      assertTrue(!out.contains("can_access_label"), out);
    }

    @Test
    @DisplayName("ANDs both write predicates into the WHERE of an UPDATE")
    void andsBothWritePredicatesOnUpdate() {
      String out = inspect(inspector, "UPDATE documents SET name = ? WHERE id = ?");
      assertTrue(out.contains("can_access_tenant(documents.tenant_id)"), out);
      assertTrue(out.contains("can_access_label(documents.label_id)"), out);
    }

    @Test
    @DisplayName("a table active only in the second dimension still trips the gate")
    void secondDimensionContributesToTheGate() {
      // The gate is the union of both dimensions' tables; a table only the label dimension knows
      // must not slip through unfiltered.
      ScopeStatementInspector labelOnly =
          new ScopeStatementInspector(
              List.of(
                  new TenantDimension(new TenantTables(Set.of(), Set.of())),
                  new LabelDimension(Set.of("reports"))));
      String out = inspect(labelOnly, "SELECT * FROM reports r WHERE r.id = ?");
      assertTrue(out.contains("can_access_label(r.label_id, true)"), out);
    }
  }

  @Nested
  @DisplayName("with no active table")
  class Inert {

    @Test
    @DisplayName("passes statements through untouched")
    void inertWhenNothingIsActive() {
      ScopeStatementInspector inspector =
          new ScopeStatementInspector(
              List.of(new TenantDimension(new TenantTables(Set.of(), Set.of()))));
      String sql = "SELECT * FROM documents d WHERE d.id = ?";
      assertEquals(sql, inspector.inspect(sql));
    }

    @Test
    @DisplayName("an empty dimension list is inert")
    void inertWithNoDimensions() {
      ScopeStatementInspector inspector = new ScopeStatementInspector(List.of());
      String sql = "SELECT * FROM documents d WHERE d.id = ?";
      assertEquals(sql, inspector.inspect(sql));
    }
  }
}
