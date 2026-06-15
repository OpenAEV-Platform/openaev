package io.openaev.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantStatementInspector — SELECT on a single table")
class TenantStatementInspectorTest {

  private final TenantStatementInspector inspector =
      new TenantStatementInspector(new TenantTables(Set.of("documents"), Set.of("groups")));

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
}
