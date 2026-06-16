package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantSqlLeakOracle — the empirical-gate leak detector")
class TenantSqlLeakOracleTest {

  private final TenantSqlLeakOracle oracle =
      new TenantSqlLeakOracle(List.of("documents", "assets", "groups"));

  private static String wrap(String table, String alias) {
    return "(SELECT * FROM "
        + table
        + " "
        + alias
        + " WHERE can_access_tenant("
        + alias
        + ".tenant_id)) AS "
        + alias;
  }

  @Test
  @DisplayName("a fully wrapped FROM is clean")
  void wrappedFromIsClean() {
    assertTrue(oracle.unwrappedTenantTables("SELECT * FROM " + wrap("documents", "d")).isEmpty());
  }

  @Test
  @DisplayName("an unwrapped FROM tenant table is a leak")
  void unwrappedFromIsLeak() {
    assertEquals(List.of("documents"), oracle.unwrappedTenantTables("SELECT * FROM documents d"));
  }

  @Test
  @DisplayName("an unwrapped JOIN tenant table is a leak")
  void unwrappedJoinIsLeak() {
    String sql = "SELECT * FROM other o JOIN documents d ON o.id = d.ref";
    assertEquals(List.of("documents"), oracle.unwrappedTenantTables(sql));
  }

  @Test
  @DisplayName("one wrapped and one unwrapped reference is still a leak (no aggregate masking)")
  void mixedReferencesIsLeak() {
    String sql = "SELECT * FROM " + wrap("documents", "a") + " JOIN documents x ON x.id = a.id";
    assertEquals(List.of("documents"), oracle.unwrappedTenantTables(sql));
  }

  @Test
  @DisplayName("a leak in one table is reported even while another table is wrapped")
  void perTableIsolation() {
    String sql =
        "SELECT * FROM "
            + wrap("groups", "g")
            + " JOIN documents d ON d.g = g.id"; // documents bare
    assertEquals(List.of("documents"), oracle.unwrappedTenantTables(sql));
  }

  @Test
  @DisplayName("a non-tenant table is never flagged")
  void nonTenantIsClean() {
    String sql = "SELECT * FROM widgets w JOIN gadgets g ON w.id = g.w";
    assertTrue(oracle.unwrappedTenantTables(sql).isEmpty());
  }

  @Test
  @DisplayName("a tenant name used only as a column is not a FROM/JOIN leak")
  void tenantNameAsColumnIsClean() {
    assertTrue(oracle.unwrappedTenantTables("SELECT documents FROM widgets w").isEmpty());
  }

  @Test
  @DisplayName("a shorter tenant name does not match a longer table name (substring safety)")
  void substringSafety() {
    // 'assets' must not match 'assets_archive'
    assertTrue(oracle.unwrappedTenantTables("SELECT * FROM assets_archive aa").isEmpty());
    assertFalse(oracle.mentioned("SELECT * FROM assets_archive aa").contains("assets"));
  }

  @Test
  @DisplayName("matching is case-insensitive")
  void caseInsensitive() {
    assertEquals(List.of("documents"), oracle.unwrappedTenantTables("select * from DOCUMENTS d"));
  }

  @Test
  @DisplayName("a tenant name inside a string literal is ignored")
  void stringLiteralIgnored() {
    String sql = "SELECT * FROM widgets w WHERE w.note = 'see from documents d'";
    assertTrue(oracle.unwrappedTenantTables(sql).isEmpty());
    assertFalse(oracle.mentioned(sql).contains("documents"));
  }

  @Test
  @DisplayName("self-join with both references wrapped is clean")
  void selfJoinWrappedIsClean() {
    String sql =
        "SELECT * FROM "
            + wrap("documents", "a")
            + " JOIN "
            + wrap("documents", "b")
            + " ON a.id = b.parent";
    assertTrue(oracle.unwrappedTenantTables(sql).isEmpty());
  }

  @Test
  @DisplayName("self-join with one reference unwrapped is a leak")
  void selfJoinPartiallyWrappedIsLeak() {
    String sql = "SELECT * FROM " + wrap("documents", "a") + " JOIN documents b ON a.id = b.parent";
    assertEquals(List.of("documents"), oracle.unwrappedTenantTables(sql));
  }

  @Test
  @DisplayName("quoted identifiers are handled")
  void quotedIdentifier() {
    assertEquals(
        List.of("documents"), oracle.unwrappedTenantTables("SELECT * FROM \"documents\" d"));
  }

  @Test
  @DisplayName("a DELETE target guarded by a WHERE predicate is not a read leak")
  void deleteTargetGuardedByPredicateIsClean() {
    String sql = "DELETE FROM documents WHERE id = ? AND (can_access_tenant(documents.tenant_id))";
    assertTrue(oracle.unwrappedTenantTables(sql).isEmpty());
  }

  @Test
  @DisplayName("a DELETE target with an alias guarded by a predicate is not a read leak")
  void deleteTargetWithAliasIsClean() {
    String sql = "DELETE FROM documents d WHERE d.id = ? AND (can_access_tenant(d.tenant_id))";
    assertTrue(oracle.unwrappedTenantTables(sql).isEmpty());
  }

  @Test
  @DisplayName("a DELETE with an unwrapped tenant sub-query read is still a leak")
  void deleteWithUnwrappedSubqueryIsLeak() {
    String sql = "DELETE FROM widgets w WHERE w.id IN (SELECT id FROM documents x)";
    assertEquals(List.of("documents"), oracle.unwrappedTenantTables(sql));
  }

  @Test
  @DisplayName("a DELETE whose same-table sub-query read is wrapped is clean")
  void deleteSameTableWrappedSubqueryIsClean() {
    String sql =
        "DELETE FROM documents WHERE id IN (SELECT id FROM " + wrap("documents", "b") + ")";
    assertTrue(oracle.unwrappedTenantTables(sql).isEmpty());
  }

  @Test
  @DisplayName("mentioned reports every tenant table present as a word")
  void mentionedReportsTenantTables() {
    Set<String> mentioned =
        oracle.mentioned("SELECT * FROM documents d JOIN groups g ON g.id = d.g");
    assertEquals(Set.of("documents", "groups"), mentioned);
  }
}
