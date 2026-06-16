package io.openaev.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantStatementInspector")
class TenantStatementInspectorTest {

  private final TenantStatementInspector inspector =
      new TenantStatementInspector(
          new TenantTables(Set.of("documents", "findings"), Set.of("groups")));

  private String inspect(String sql) {
    return inspector.inspect(sql).replaceAll("\\s+", " ").trim();
  }

  // --- Single table --------------------------------------------------------

  @Test
  @DisplayName("wraps a strict tenant table with can_access_tenant on its tenant_id")
  void strictTableIsFiltered() {
    String out = inspect("SELECT * FROM documents d WHERE d.id = ?");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
  }

  @Test
  @DisplayName("a dual-scope table also lets platform rows through (allow_platform = true)")
  void dualScopeTableIsFiltered() {
    String out = inspect("SELECT * FROM groups g WHERE g.id = ?");
    assertTrue(out.contains("can_access_tenant(g.tenant_id, true)"), out);
  }

  @Test
  @DisplayName("a non-tenant table is left untouched")
  void nonTenantTableUntouched() {
    assertFalse(inspect("SELECT * FROM users u WHERE u.id = ?").contains("can_access_tenant"));
  }

  @Test
  @DisplayName("table matching ignores the case of the configured names")
  void configuredNamesAreCaseInsensitive() {
    TenantStatementInspector upper =
        new TenantStatementInspector(new TenantTables(Set.of("Documents"), Set.of()));
    String out =
        upper.inspect("SELECT * FROM documents d WHERE d.id = ?").replaceAll("\\s+", " ").trim();
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
  }

  @Test
  @DisplayName("a table without an alias is filtered on its own name")
  void tableWithoutAlias() {
    String out = inspect("SELECT * FROM documents WHERE id = ?");
    assertTrue(out.contains("can_access_tenant(documents.tenant_id)"), out);
  }

  // --- Joins ---------------------------------------------------------------

  @Test
  @DisplayName("an inner join filters both tenant tables")
  void innerJoinFiltersBothTables() {
    String out = inspect("SELECT * FROM documents d JOIN findings f ON f.doc_id = d.id");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("a left join filters the joined table and stays a left join")
  void leftJoinFiltersAndStaysOuter() {
    String out = inspect("SELECT * FROM documents d LEFT JOIN findings f ON f.doc_id = d.id");
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
    assertTrue(out.toUpperCase().contains("LEFT JOIN"), out);
  }

  @Test
  @DisplayName("a join to a non-tenant table leaves that table untouched")
  void joinToNonTenantTableUntouched() {
    String out = inspect("SELECT * FROM documents d JOIN users u ON u.id = d.user_id");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertFalse(out.contains("can_access_tenant(u."), out);
  }

  @Test
  @DisplayName("an implicit (comma) join filters both tenant tables")
  void commaJoinFiltersBothTables() {
    String out = inspect("SELECT * FROM documents d, findings f WHERE f.doc_id = d.id");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  // --- Sub-queries and CTEs ------------------------------------------------

  @Test
  @DisplayName("a WHERE sub-query on another tenant table is filtered too")
  void whereSubqueryFiltersBothTables() {
    String out = inspect("SELECT * FROM documents d WHERE d.x IN (SELECT f.x FROM findings f)");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("a WHERE sub-query on the same tenant table is filtered at both levels")
  void whereSubquerySameTableFiltered() {
    String out = inspect("SELECT * FROM documents d WHERE d.x IN (SELECT x FROM documents)");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(documents.tenant_id)"), out);
  }

  @Test
  @DisplayName("a tenant table in a CTE is filtered")
  void cteFiltered() {
    String out = inspect("WITH c AS (SELECT * FROM findings) SELECT * FROM documents");
    assertTrue(out.contains("can_access_tenant(documents.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(findings.tenant_id)"), out);
  }

  @Test
  @DisplayName("a CTE on a same-named tenant table is filtered at both levels")
  void sameNameCteFiltered() {
    String out = inspect("WITH c AS (SELECT * FROM documents) SELECT * FROM documents d");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(documents.tenant_id)"), out);
  }

  @Test
  @DisplayName("a scalar tenant sub-query in the select list is filtered")
  void scalarSelectSubqueryFiltered() {
    String out = inspect("SELECT d.id, (SELECT count(*) FROM findings f) AS n FROM documents d");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("a tenant sub-query in a join condition is filtered")
  void joinConditionSubqueryFiltered() {
    String out =
        inspect(
            "SELECT * FROM documents d JOIN users u ON u.id = d.user_id"
                + " AND u.id IN (SELECT f.user_id FROM findings f)");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
    assertFalse(out.contains("can_access_tenant(u."), out);
  }

  @Test
  @DisplayName("a tenant sub-query inside a UNION is filtered")
  void unionSubqueryFiltered() {
    String out =
        inspect(
            "SELECT * FROM documents d WHERE d.x IN"
                + " (SELECT a FROM findings f UNION SELECT b FROM findings f2)");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f2.tenant_id)"), out);
  }

  @Test
  @DisplayName("a FROM-derived table is filtered on its inner table")
  void fromDerivedTableFiltered() {
    String out = inspect("SELECT * FROM (SELECT * FROM documents d) x WHERE x.id = ?");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
  }

  @Test
  @DisplayName("a tenant table in an EXISTS sub-query is filtered")
  void existsSubqueryFiltered() {
    String out =
        inspect(
            "SELECT * FROM documents d WHERE EXISTS (SELECT 1 FROM findings f WHERE f.x = d.x)");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("a FROM-derived UNION filters both inner branches")
  void fromDerivedUnionFiltered() {
    String out =
        inspect("SELECT * FROM (SELECT * FROM documents d UNION SELECT * FROM findings f) x");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("a top-level UNION filters both branches")
  void topLevelUnionFiltered() {
    String out = inspect("SELECT * FROM documents d UNION SELECT * FROM findings f");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("a sub-query on a non-tenant table is left untouched; the main table stays filtered")
  void nonTenantSubqueryUntouched() {
    String out = inspect("SELECT * FROM documents d WHERE d.user_id IN (SELECT u.id FROM users u)");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertFalse(out.contains("can_access_tenant(u."), out);
  }

  // --- UPDATE / DELETE -----------------------------------------------------

  @Test
  @DisplayName("an UPDATE adds the tenant filter to its WHERE")
  void updateWithWhereFiltered() {
    String out = inspect("UPDATE documents d SET x = 1 WHERE d.id = ?");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
  }

  @Test
  @DisplayName("an UPDATE without a WHERE gets one with the tenant filter")
  void updateWithoutWhereFiltered() {
    String out = inspect("UPDATE documents SET x = 1");
    assertTrue(out.contains("can_access_tenant(documents.tenant_id)"), out);
  }

  @Test
  @DisplayName("an UPDATE on a non-tenant table is left untouched")
  void updateNonTenantUntouched() {
    assertFalse(inspect("UPDATE users SET x = 1 WHERE id = ?").contains("can_access_tenant"));
  }

  @Test
  @DisplayName("an UPDATE with a tenant sub-query filters both the target and the sub-query")
  void updateWhereSubqueryFiltered() {
    String out = inspect("UPDATE documents d SET x = 1 WHERE d.y IN (SELECT f.y FROM findings f)");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("a DELETE adds the tenant filter to its WHERE")
  void deleteFiltered() {
    String out = inspect("DELETE FROM documents d WHERE d.id = ?");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
  }

  @Test
  @DisplayName("a DELETE without an alias filters on the table name")
  void deleteNoAliasFiltered() {
    String out = inspect("DELETE FROM documents WHERE id = ?");
    assertTrue(out.contains("can_access_tenant(documents.tenant_id)"), out);
  }

  @Test
  @DisplayName("a write to a dual-scope table does not reach platform rows (no allow_platform)")
  void updateDualScopeTargetExcludesPlatform() {
    String out = inspect("UPDATE groups g SET x = 1 WHERE g.id = ?");
    assertTrue(out.contains("can_access_tenant(g.tenant_id)"), out);
    assertFalse(out.contains("can_access_tenant(g.tenant_id, true)"), out);
  }

  // --- INSERT --------------------------------------------------------------

  @Test
  @DisplayName("an INSERT ... SELECT filters its source query")
  void insertSelectFiltersSource() {
    String out = inspect("INSERT INTO documents (id, x) SELECT f.id, f.x FROM findings f");
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName(
      "an INSERT ... VALUES passes through (write-side tenant assignment is the listener's)")
  void insertValuesPassesThrough() {
    assertFalse(inspect("INSERT INTO documents (id) VALUES (1)").contains("can_access_tenant"));
  }

  @Test
  @DisplayName("a tenant sub-query inside INSERT ... VALUES is filtered")
  void insertValuesSubqueryFiltered() {
    String out = inspect("INSERT INTO documents (id) VALUES ((SELECT max(f.x) FROM findings f))");
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("the INSERT target table is left intact (not wrapped)")
  void insertTargetStaysIntact() {
    String out = inspect("INSERT INTO documents (id, x) SELECT f.id, f.x FROM findings f");
    assertTrue(out.contains("INSERT INTO documents"), out);
    assertFalse(out.contains("can_access_tenant(documents"), out);
  }

  @Test
  @DisplayName("an INSERT ... ON CONFLICT DO NOTHING passes")
  void insertOnConflictDoNothingPasses() {
    assertDoesNotThrow(
        () ->
            inspector.inspect("INSERT INTO documents (id) VALUES (1) ON CONFLICT (id) DO NOTHING"));
  }

  @Test
  @DisplayName("an INSERT ... ON CONFLICT DO UPDATE on a tenant table is rejected (fail-closed)")
  void insertOnConflictDoUpdateFailsClosed() {
    assertThrows(
        TenantFilteringException.class,
        () ->
            inspector.inspect(
                "INSERT INTO documents (id, x) VALUES (1, 2) ON CONFLICT (id) DO UPDATE SET x = 2"));
  }

  // --- Fail-closed ---------------------------------------------------------

  @Test
  @DisplayName("unparseable SQL is rejected (fail-closed)")
  void unparseableFailsClosed() {
    assertThrows(TenantFilteringException.class, () -> inspector.inspect("NOT SQL AT ALL ;;;"));
  }

  @Test
  @DisplayName("an UPDATE ... FROM is rejected (fail-closed)")
  void updateFromFailsClosed() {
    assertThrows(
        TenantFilteringException.class,
        () -> inspector.inspect("UPDATE documents d SET x = 1 FROM findings f WHERE f.id = d.fid"));
  }

  @Test
  @DisplayName("a DELETE ... USING is rejected (fail-closed)")
  void deleteUsingFailsClosed() {
    assertThrows(
        TenantFilteringException.class,
        () -> inspector.inspect("DELETE FROM documents d USING findings f WHERE f.id = d.fid"));
  }
}
