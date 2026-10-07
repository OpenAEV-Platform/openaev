package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the derived marking dimension in isolation: the predicates it emits for the registered
 * tables, how they compose with the tenant and marking dimensions inside the single inspector, the
 * query shapes actually used on {@code injects} and {@code findings}, and the rollout knob.
 *
 * <p>What the predicates mean against real rows is proven end to end by {@code
 * AtomicTestingMarkingHideParentTest}; this test pins their shape.
 */
@DisplayName("DerivedMarkingDimension")
class DerivedMarkingDimensionTest {

  private static final DerivedMarkedTables ACTIVE =
      DerivedMarkedTables.REGISTRY.restrictTo(List.of("injects", "findings"));

  private static final String INJECT_PREDICATE =
      "(i.inject_scenario IS NOT NULL OR i.inject_exercise IS NOT NULL"
          + " OR can_see_inject(i.inject_id))";

  private static String flatten(String sql) {
    return sql.replaceAll("\\s+", " ").trim();
  }

  private static long placeholders(String sql) {
    return sql.chars().filter(c -> c == '?').count();
  }

  @Nested
  @DisplayName("predicate")
  class Predicate {

    private final DerivedMarkingDimension dimension = new DerivedMarkingDimension(ACTIVE);

    @Test
    @DisplayName("guards injects to root injects (Atomic Testing) and parenthesizes the OR")
    void given_injects_should_guardToAtomicTesting() {
      assertEquals(INJECT_PREDICATE, dimension.readPredicate("injects", "i"));
    }

    @Test
    @DisplayName("checks a finding through its own id and its inject")
    void given_findings_should_checkFindingAndInject() {
      assertEquals(
          "(can_see_finding(f.finding_id, f.finding_inject_id))",
          dimension.readPredicate("findings", "f"));
    }

    @Test
    @DisplayName("uses the same predicate for reads and writes")
    void given_write_should_matchRead() {
      assertEquals(
          dimension.readPredicate("injects", "i"), dimension.writePredicate("injects", "i"));
    }

    @Test
    @DisplayName("declares no write attribution: nothing is stored")
    void given_dimension_should_declareNoWriteAttribution() {
      assertNull(dimension.writeAttributionColumn());
    }

    @Test
    @DisplayName("lists every SQL function the active predicates call")
    void given_activeTables_should_listFunctions() {
      assertEquals(
          Set.of("can_see_inject", "can_see_asset_group", "can_see_finding"), ACTIVE.functions());
    }
  }

  @Nested
  @DisplayName("inside the inspector")
  class InsideTheInspector {

    // injects is still on tenant v1 (@Filter): absent from the tenant tables. findings is v2.
    private final TenantDimension tenant =
        new TenantDimension(new TenantTables(Set.of("findings", "assets"), Set.of()));
    private final MarkingDimension marking =
        new MarkingDimension(new MarkedTables(Map.of("assets", new MarkedTable("assets"))));
    private final ScopeStatementInspector inspector =
        new ScopeStatementInspector(List.of(tenant, marking, new DerivedMarkingDimension(ACTIVE)));

    private String rewrite(String sql) {
      String out = inspector.inspect(sql);
      assertEquals(
          placeholders(sql),
          placeholders(out),
          "the rewrite must not change the placeholder count: " + out);
      return flatten(out);
    }

    @Test
    @DisplayName("an empty allowlist leaves the emitted SQL byte-identical")
    void given_noDerivedTable_should_beInert() {
      ScopeStatementInspector withDerived =
          new ScopeStatementInspector(
              List.of(tenant, marking, new DerivedMarkingDimension(DerivedMarkedTables.EMPTY)));
      ScopeStatementInspector without = new ScopeStatementInspector(List.of(tenant, marking));
      for (String sql :
          List.of(
              "SELECT * FROM injects i WHERE i.inject_id = ?",
              "SELECT * FROM findings f WHERE f.finding_id = ?",
              "UPDATE injects SET inject_title = ? WHERE inject_id = ?")) {
        assertEquals(without.inspect(sql), withDerived.inspect(sql), sql);
      }
    }

    @Test
    @DisplayName("narrows a primary FROM injects through the WHERE, keeping the v1 condition")
    void given_primaryInjects_should_narrowWhere() {
      String out = rewrite("SELECT i.inject_id FROM injects i WHERE i.tenant_id = ?");
      assertTrue(out.contains("WHERE (i.tenant_id = ?) AND (" + INJECT_PREDICATE + ")"), out);
    }

    @Test
    @DisplayName("ANDs the tenant predicate and the derived one on findings")
    void given_primaryFindings_should_composeWithTenant() {
      String out = rewrite("SELECT f.finding_id FROM findings f WHERE f.finding_value = ?");
      assertTrue(
          out.contains(
              "AND (can_access_tenant(f.tenant_id) AND"
                  + " (can_see_finding(f.finding_id, f.finding_inject_id)))"),
          out);
    }

    @Test
    @DisplayName("filters findings even when injects is only LEFT JOINed and wrapped")
    void given_findingsLeftJoinInjects_should_filterBoth() {
      String out =
          rewrite(
              "SELECT f.finding_id FROM findings f"
                  + " LEFT JOIN injects i ON i.inject_id = f.finding_inject_id"
                  + " WHERE f.finding_value = ?");
      // The LEFT JOIN alone would keep the finding with a NULL inject: the finding's own predicate
      // is what hides it.
      assertTrue(out.contains("can_see_finding(f.finding_id, f.finding_inject_id)"), out);
      assertTrue(out.contains("LEFT JOIN (SELECT * FROM injects i WHERE " + INJECT_PREDICATE), out);
    }

    @Test
    @DisplayName("guards an UPDATE on injects so a hidden row is untouched")
    void given_updateInjects_should_guardWhere() {
      String out = rewrite("UPDATE injects SET inject_title = ? WHERE inject_id = ?");
      assertTrue(out.contains("can_see_inject(injects.inject_id)"), out);
    }

    @Test
    @DisplayName("guards the DO UPDATE of a findings upsert")
    void given_findingsUpsert_should_guardDoUpdate() {
      String out =
          rewrite(
              "INSERT INTO findings (finding_id, finding_value, finding_inject_id, tenant_id)"
                  + " VALUES (?, ?, ?, ?) ON CONFLICT (finding_id)"
                  + " DO UPDATE SET finding_value = EXCLUDED.finding_value");
      assertTrue(out.contains("can_see_finding(findings.finding_id"), out);
    }
  }

  @Nested
  @DisplayName("activation allowlist")
  class Allowlist {

    @Test
    @DisplayName("keeps only the allowlisted tables")
    void given_allowlist_should_keepOnlyListed() {
      assertEquals(
          Set.of("findings"),
          DerivedMarkedTables.REGISTRY.restrictTo(List.of("findings")).tableNames());
    }

    @Test
    @DisplayName("an empty allowlist activates nothing")
    void given_emptyAllowlist_should_beInert() {
      assertTrue(DerivedMarkedTables.REGISTRY.restrictTo(List.of()).tableNames().isEmpty());
    }

    @Test
    @DisplayName("an unregistered table fails fast rather than staying unprotected")
    void given_unknownTable_should_failFast() {
      IllegalArgumentException error =
          assertThrows(
              IllegalArgumentException.class,
              () -> DerivedMarkedTables.REGISTRY.restrictTo(List.of("scenarios")));
      assertTrue(error.getMessage().contains("scenarios"), error.getMessage());
    }

    @Test
    @DisplayName("matches table names case-insensitively")
    void given_mixedCase_should_match() {
      DerivedMarkedTables active = DerivedMarkedTables.REGISTRY.restrictTo(List.of("Injects"));
      assertEquals("injects", active.get("INJECTS").table());
    }
  }
}
