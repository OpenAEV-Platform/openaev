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
    String sql = "SELECT * FROM users u WHERE u.id = ?";
    assertFalse(inspect(sql).toLowerCase().contains("can_access_tenant"));
  }

  @Test
  @DisplayName("unparseable SQL is rejected (fail-closed)")
  void unparseableFailsClosed() {
    assertThrows(RuntimeException.class, () -> inspector.inspect("NOT SQL AT ALL ;;;"));
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

  @Test
  @DisplayName("a tenant table in a WHERE sub-query is rejected (fail-closed)")
  void whereSubqueryOnAnotherTenantTableFailsClosed() {
    assertThrows(
        RuntimeException.class,
        () ->
            inspector.inspect(
                "SELECT * FROM documents d WHERE d.x IN (SELECT f.x FROM findings f)"));
  }

  @Test
  @DisplayName("a same-named tenant table in a sub-query is rejected (fail-closed)")
  void whereSubqueryOnSameTenantTableFailsClosed() {
    assertThrows(
        RuntimeException.class,
        () ->
            inspector.inspect("SELECT * FROM documents d WHERE d.x IN (SELECT x FROM documents)"));
  }

  @Test
  @DisplayName("a tenant table in a CTE is rejected (fail-closed)")
  void cteOnTenantTableFailsClosed() {
    assertThrows(
        RuntimeException.class,
        () -> inspector.inspect("WITH c AS (SELECT * FROM findings) SELECT * FROM documents"));
  }
}
