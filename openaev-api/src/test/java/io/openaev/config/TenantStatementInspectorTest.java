package io.openaev.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
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

  // --- The active-table gate ignores string literals -----------------------

  @Test
  @DisplayName("a tenant table name only inside a string literal does not trip the gate")
  void tableNameInStringLiteralDoesNotTripTheGate() {
    // The statement touches no active table; "documents" appears only inside a literal, and the
    // statement is not parseable. The gate must not pull it into rewriting and fail-close on it.
    String sql = "RELOAD CONFIGURATION FOR 'documents' SET something = ON";
    assertEquals(sql, inspector.inspect(sql));
  }

  // --- Fail-closed on shapes the rewriter does not cover -------------------

  @Test
  @DisplayName("a parseable but non-CRUD statement on an active table is refused")
  void unsupportedStatementTypeIsRefused() {
    TenantFilteringException ex =
        assertThrows(
            TenantFilteringException.class, () -> inspector.inspect("TRUNCATE TABLE documents"));
    assertTrue(ex.getMessage().contains("statement shape not supported"), ex.getMessage());
  }

  @Test
  @DisplayName("an UPDATE with a target-side join is refused (not yet covered)")
  void updateWithTargetJoinIsRefused() {
    TenantFilteringException ex =
        assertThrows(
            TenantFilteringException.class,
            () ->
                inspector.inspect(
                    "UPDATE documents d JOIN findings f ON f.doc_id = d.id SET d.name = 'x'"));
    assertTrue(ex.getMessage().contains("target join"), ex.getMessage());
  }

  @Test
  @DisplayName("a multi-target DELETE is refused (not yet covered)")
  void multiTargetDeleteIsRefused() {
    TenantFilteringException ex =
        assertThrows(
            TenantFilteringException.class,
            () ->
                inspector.inspect(
                    "DELETE d, f FROM documents d JOIN findings f ON f.doc_id = d.id"));
    assertTrue(ex.getMessage().contains("multi-target or join"), ex.getMessage());
  }

  @Test
  @DisplayName("an INSERT ... SELECT without a column list is refused")
  void insertSelectWithoutColumnListIsRefused() {
    TenantFilteringException ex =
        assertThrows(
            TenantFilteringException.class,
            () -> inspector.inspect("INSERT INTO documents SELECT id FROM documents"));
    assertTrue(ex.getMessage().contains("explicit column list"), ex.getMessage());
  }

  @Test
  @DisplayName("an INSERT ... SELECT from a non-plain (UNION) source is refused")
  void insertSelectFromUnionSourceIsRefused() {
    TenantFilteringException ex =
        assertThrows(
            TenantFilteringException.class,
            () ->
                inspector.inspect(
                    "INSERT INTO documents (tenant_id, x) "
                        + "SELECT tenant_id, x FROM documents UNION SELECT tenant_id, x FROM documents"));
    assertTrue(ex.getMessage().contains("explicit column list"), ex.getMessage());
  }

  @Test
  @DisplayName("an INSERT ... SELECT whose tenant_id column is not projected is refused")
  void insertSelectTenantIdNotProjectedIsRefused() {
    TenantFilteringException ex =
        assertThrows(
            TenantFilteringException.class,
            () ->
                inspector.inspect("INSERT INTO documents (x, tenant_id) SELECT x FROM documents"));
    assertTrue(ex.getMessage().contains("cannot be mapped"), ex.getMessage());
  }

  @Test
  @DisplayName("a FROM item the rewriter cannot wrap (table function) is refused")
  void unsupportedFromItemIsRefused() {
    TenantFilteringException ex =
        assertThrows(
            TenantFilteringException.class,
            () ->
                inspector.inspect("SELECT * FROM documents d CROSS JOIN generate_series(1, 10) g"));
    assertTrue(ex.getMessage().contains("not yet covered"), ex.getMessage());
  }

  @Test
  @DisplayName("a LATERAL table function is accepted (unnests an existing row, not a whole table)")
  void lateralTableFunctionIsAccepted() {
    String out =
        inspect(
            "SELECT d.id, e.value FROM documents d"
                + " LEFT JOIN LATERAL jsonb_array_elements(d.payload) e ON true");
    // The documents table is still filtered, but the lateral function is not refused
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertFalse(out.contains("not yet covered"), out);
  }

  @Test
  @DisplayName("a LATERAL sub-select is accepted alongside tenant table filtering")
  void lateralSubSelectIsAccepted() {
    String out =
        inspect(
            "SELECT d.id, sub.x FROM documents d"
                + " LEFT JOIN LATERAL (SELECT f.x FROM findings f WHERE f.doc_id = d.id LIMIT 1) sub ON true");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    // The inner findings table in the lateral sub-select is also filtered
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("a LATERAL table function as the sole FROM of a sub-query is accepted")
  void lateralTableFunctionAsSubqueryFromIsAccepted() {
    // The collector "not filled" predicate shape: NOT EXISTS over a jsonb unnest of the current
    // row. LATERAL is a noise word for a function-call FROM item in PostgreSQL but is what marks
    // the shape as reviewed-safe for the rewriter (#7007).
    String out =
        inspect(
            "SELECT * FROM documents d WHERE NOT EXISTS"
                + " (SELECT 1 FROM LATERAL jsonb_array_elements(d.payload::jsonb) r"
                + " WHERE r->>'sourceId' = :sourceId)");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("jsonb_array_elements"), out);
  }

  @Test
  @DisplayName("the AI defense collector query passes with collectors active (regression #7007)")
  void aiDefenseExpectationsQueryPassesWithCollectorsActive() throws Exception {
    // #6751 activated collectors, which pulled findAgentlessExpectationsNotFilledForSource into
    // rewriting; its jsonb_array_elements predicate was refused and the AI expectations endpoint
    // returned 500 TENANT_FILTERING_REFUSED to every AI defense collector. Pin the real production
    // SQL against an inspector with collectors active (dual-scope, tenant_id is nullable).
    String sql =
        io.openaev.database.repository.InjectExpectationRepository.class
            .getMethod(
                "findAgentlessExpectationsNotFilledForSource",
                String.class,
                String.class,
                String.class,
                int.class)
            .getAnnotation(org.springframework.data.jpa.repository.Query.class)
            .value();
    TenantStatementInspector collectorsActive =
        new TenantStatementInspector(new TenantTables(Set.of(), Set.of("collectors")));
    String out = collectorsActive.inspect(sql).replaceAll("\\s+", " ").trim();
    // The collectors read source is filtered (platform rows allowed: dual-scope read)...
    assertTrue(out.contains("can_access_tenant(c.tenant_id, true)"), out);
    // ...and the result predicate survives the rewrite instead of being refused.
    assertTrue(out.contains("jsonb_array_elements"), out);
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

  // --- Parenthesized join group --------------------------------------------

  @Test
  @DisplayName("a parenthesized join group filters the tenant tables inside it")
  void parenthesizedJoinGroupFiltered() {
    String out =
        inspect("SELECT * FROM (documents d JOIN findings f ON f.doc_id = d.id) WHERE d.id = ?");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("a parenthesized group joined to an outer table filters every tenant table")
  void parenthesizedJoinGroupJoinedOutside() {
    String out =
        inspect(
            "SELECT * FROM (documents d JOIN users u ON u.id = d.user_id)"
                + " JOIN findings f ON f.x = d.id");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
    assertFalse(out.contains("can_access_tenant(u."), out);
  }

  @Test
  @DisplayName("a parenthesized group of only non-tenant tables is left untouched")
  void parenthesizedNonTenantGroupUntouched() {
    String out = inspect("SELECT * FROM (users u JOIN roles r ON r.id = u.role_id)");
    assertFalse(out.contains("can_access_tenant"), out);
  }

  @Test
  @DisplayName("a nested parenthesized group filters tenant tables at every depth")
  void nestedParenthesizedGroupFiltered() {
    String out =
        inspect(
            "SELECT * FROM ((documents d JOIN findings f ON f.doc_id = d.id)"
                + " JOIN groups g ON g.x = d.id)");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(g.tenant_id, true)"), out);
  }

  @Test
  @DisplayName("a left join to a parenthesized group stays a left join and filters inside it")
  void leftJoinToParenthesizedGroupStaysOuter() {
    String out =
        inspect(
            "SELECT * FROM documents d"
                + " LEFT JOIN (findings f JOIN users u ON u.id = f.uid) ON f.doc_id = d.id");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
    assertFalse(out.contains("can_access_tenant(u."), out);
    assertTrue(out.toUpperCase().contains("LEFT JOIN"), out);
  }

  @Test
  @DisplayName("a sub-query inside a parenthesized group is filtered (collector reaches into it)")
  void subqueryInsideParenthesizedGroupFiltered() {
    String out =
        inspect("SELECT * FROM (documents d JOIN (SELECT * FROM findings) s ON s.doc_id = d.id)");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(findings.tenant_id)"), out);
  }

  @Test
  @DisplayName("the rewritten parenthesized group is valid, re-parsable SQL")
  void parenthesizedGroupOutputIsValidSql() {
    String out =
        inspector.inspect("SELECT * FROM (documents d JOIN findings f ON f.doc_id = d.id)");
    assertDoesNotThrow(() -> CCJSqlParserUtil.parse(out));
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
  @DisplayName("an INSERT ... SELECT into a non-tenant table still filters its source query")
  void insertSelectFiltersSource() {
    String out = inspect("INSERT INTO users (id, x) SELECT f.id, f.x FROM findings f");
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName(
      "INSERT ... SELECT into a tenant table validates the written tenant_id and filters the source")
  void insertSelectIntoTenantValidatesWriteAndFiltersSource() {
    String out = inspect("INSERT INTO documents (tenant_id, x) SELECT f.dest, f.x FROM findings f");
    assertTrue(out.contains("can_access_tenant(f.dest)"), out); // written tenant_id, validated
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out); // source read, filtered
    assertTrue(out.contains("INSERT INTO documents"), out);
    assertFalse(out.contains("can_access_tenant(documents"), out); // target not wrapped
  }

  @Test
  @DisplayName("INSERT ... SELECT into a tenant table without tenant_id is refused (fail-closed)")
  void insertSelectIntoTenantWithoutTenantIdFailsClosed() {
    assertThrows(
        TenantFilteringException.class,
        () -> inspector.inspect("INSERT INTO documents (id, x) SELECT u.id, u.x FROM users u"));
  }

  @Test
  @DisplayName("INSERT ... SELECT * into a tenant table is refused (tenant_id not mappable)")
  void insertSelectStarIntoTenantFailsClosed() {
    assertThrows(
        TenantFilteringException.class,
        () -> inspector.inspect("INSERT INTO documents (tenant_id, x) SELECT * FROM users u"));
  }

  @Test
  @DisplayName("INSERT ... SELECT with a bind-parameter tenant_id is refused (would break binding)")
  void insertSelectBindParameterTenantFailsClosed() {
    assertThrows(
        TenantFilteringException.class,
        () -> inspector.inspect("INSERT INTO documents (tenant_id, x) SELECT ?, u.x FROM users u"));
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
  @DisplayName("an INSERT ... ON CONFLICT DO NOTHING passes")
  void insertOnConflictDoNothingPasses() {
    assertDoesNotThrow(
        () ->
            inspector.inspect("INSERT INTO documents (id) VALUES (1) ON CONFLICT (id) DO NOTHING"));
  }

  @Test
  @DisplayName(
      "ON CONFLICT DO UPDATE on a tenant table is guarded (a cross-tenant row stays untouched)")
  void insertOnConflictDoUpdateGuarded() {
    String out =
        inspect("INSERT INTO documents (id, x) VALUES (1, 2) ON CONFLICT (id) DO UPDATE SET x = 2");
    assertTrue(out.contains("can_access_tenant(documents.tenant_id)"), out);
  }

  @Test
  @DisplayName("ON CONFLICT DO UPDATE keeps an existing DO UPDATE WHERE and adds the tenant guard")
  void insertOnConflictDoUpdateKeepsExistingWhere() {
    String out =
        inspect(
            "INSERT INTO documents (id, x) VALUES (1, 2)"
                + " ON CONFLICT (id) DO UPDATE SET x = 2 WHERE documents.x < 5");
    assertTrue(out.contains("documents.x < 5"), out);
    assertTrue(out.contains("can_access_tenant(documents.tenant_id)"), out);
  }

  @Test
  @DisplayName("ON CONFLICT DO UPDATE on a non-tenant table is left untouched")
  void insertOnConflictDoUpdateNonTenantUntouched() {
    String out =
        inspect("INSERT INTO users (id, x) VALUES (1, 2) ON CONFLICT (id) DO UPDATE SET x = 2");
    assertFalse(out.contains("can_access_tenant"), out);
  }

  @Test
  @DisplayName(
      "the refactored finding upsert (ON CONFLICT DO UPDATE ... RETURNING) parses and is guarded")
  void findingUpsertWithReturningGuarded() {
    String out =
        inspect(
            "INSERT INTO findings (finding_id, finding_value, tenant_id) VALUES (?, ?, ?)"
                + " ON CONFLICT (finding_inject_id, finding_field, finding_type, finding_value)"
                + " DO UPDATE SET finding_name = EXCLUDED.finding_name RETURNING finding_id");
    assertTrue(out.contains("can_access_tenant(findings.tenant_id)"), out);
  }

  // --- Activation gate: only statements touching an active table are inspected ---

  @Test
  @DisplayName("an empty allowlist inspects nothing, even unparseable SQL")
  void emptyAllowlistInspectsNothing() {
    TenantStatementInspector inactive =
        new TenantStatementInspector(new TenantTables(Set.of(), Set.of()));
    assertEquals("NOT SQL AT ALL ;;;", inactive.inspect("NOT SQL AT ALL ;;;"));
    assertEquals(
        "SELECT * FROM documents d WHERE d.id = ?",
        inactive.inspect("SELECT * FROM documents d WHERE d.id = ?"));
  }

  @Test
  @DisplayName("a statement touching no active table is returned verbatim, not re-serialized")
  void noActiveTableReturnedVerbatim() {
    String sql = "SELECT   *   FROM users u WHERE u.id = ?";
    assertEquals(sql, inspector.inspect(sql));
  }

  @Test
  @DisplayName("unparseable SQL touching no active table passes through (no fail-close)")
  void unparseableWithoutActiveTablePassesThrough() {
    String sql = "@@@ not valid sql audit_log ;;;";
    assertEquals(sql, inspector.inspect(sql));
  }

  @Test
  @DisplayName("a table name is matched on word boundaries, not as a substring")
  void tableNameMatchedOnWordBoundary() {
    TenantStatementInspector onAsset =
        new TenantStatementInspector(new TenantTables(Set.of("asset"), Set.of()));
    String sql = "SELECT * FROM asset_groups ag WHERE ag.id = ?";
    assertEquals(sql, onAsset.inspect(sql));
  }

  @Test
  @DisplayName("the gate fires on a quoted identifier and the table is still filtered")
  void quotedIdentifierIsFiltered() {
    String out = inspect("SELECT * FROM \"documents\" d WHERE d.id = ?");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
  }

  @Test
  @DisplayName("the gate fires on a schema-qualified name and the table is still filtered")
  void schemaQualifiedNameIsFiltered() {
    String out = inspect("SELECT * FROM public.documents d WHERE d.id = ?");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
  }

  @Test
  @DisplayName("null SQL is returned as-is, never an NPE")
  void nullSqlReturnedAsIs() {
    assertNull(inspector.inspect(null));
  }

  // --- finding repository statements (T5a Part B) --------------------------

  @Test
  @DisplayName("the old modifying CTE on findings fails closed (why it was split)")
  void modifyingCteOnFindingsFailsClosed() {
    assertThrows(
        TenantFilteringException.class,
        () ->
            inspector.inspect(
                "WITH x AS (INSERT INTO findings (finding_id) VALUES (?) RETURNING finding_id)"
                    + " SELECT finding_id FROM x"));
  }

  @Test
  @DisplayName("the split finding_assets insert passes (join table, not tenant-scoped)")
  void findingAssetsInsertPasses() {
    String sql =
        "INSERT INTO findings_assets (finding_id, asset_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
    assertEquals(sql, inspector.inspect(sql));
  }

  @Test
  @DisplayName("the split finding_tags insert ... select passes (join table, not tenant-scoped)")
  void findingTagsInsertSelectPasses() {
    String sql =
        "INSERT INTO findings_tags (finding_id, tag_id)"
            + " SELECT ?, tag_id FROM unnest(CAST(? AS varchar[])) AS tag_id ON CONFLICT DO NOTHING";
    assertEquals(sql, inspector.inspect(sql));
  }

  // --- Fail-closed ---------------------------------------------------------

  @Test
  @DisplayName("unparseable SQL that touches an active table is rejected (fail-closed)")
  void unparseableTouchingActiveTableFailsClosed() {
    assertThrows(
        TenantFilteringException.class, () -> inspector.inspect("NOT SQL AT ALL documents ;;;"));
  }

  // --- UPDATE ... FROM / DELETE ... USING (multi-table) --------------------

  @Test
  @DisplayName("UPDATE ... FROM filters the target (write) and the read source")
  void updateFromFiltersTargetAndSource() {
    String out = inspect("UPDATE documents d SET x = 1 FROM findings f WHERE f.id = d.fid");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("UPDATE ... FROM filters every read source (comma list -> joins)")
  void updateFromFiltersEverySource() {
    String out =
        inspect(
            "UPDATE documents d SET x = 1 FROM findings f, groups g"
                + " WHERE f.id = d.fid AND g.id = d.gid");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(g.tenant_id, true)"), out);
  }

  @Test
  @DisplayName("UPDATE ... FROM lets a dual-scope read source see platform rows")
  void updateFromDualSourceAllowsPlatform() {
    String out = inspect("UPDATE documents d SET x = 1 FROM groups g WHERE g.id = d.gid");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(g.tenant_id, true)"), out);
  }

  @Test
  @DisplayName("UPDATE ... FROM leaves a non-tenant read source untouched")
  void updateFromNonTenantSourceUntouched() {
    String out = inspect("UPDATE documents d SET x = 1 FROM users u WHERE u.id = d.uid");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertFalse(out.contains("can_access_tenant(u"), out);
  }

  @Test
  @DisplayName("DELETE ... USING filters the target (write) and the read source")
  void deleteUsingFiltersTargetAndSource() {
    String out = inspect("DELETE FROM documents d USING findings f WHERE f.id = d.fid");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
  }

  @Test
  @DisplayName("DELETE ... USING lets a dual-scope read source see platform rows")
  void deleteUsingDualSourceAllowsPlatform() {
    String out = inspect("DELETE FROM documents d USING groups g WHERE g.id = d.gid");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(g.tenant_id, true)"), out);
  }

  @Test
  @DisplayName("DELETE ... USING filters every read source")
  void deleteUsingFiltersEverySource() {
    String out =
        inspect(
            "DELETE FROM documents d USING findings f, groups g"
                + " WHERE f.id = d.fid AND g.id = d.gid");
    assertTrue(out.contains("can_access_tenant(d.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(f.tenant_id)"), out);
    assertTrue(out.contains("can_access_tenant(g.tenant_id, true)"), out);
  }
}
