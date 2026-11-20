package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the tables <i>derived</i> from a marked one (Solution B of the task 4 design): the
 * predicate they get, how the inspector applies it on every statement shape, the startup checks
 * that keep a mis-wired link from leaving a table silently unprotected, and the registry of {@link
 * MarkingDerivedTables}.
 *
 * <p>The semantics against real rows are the job of the database-backed test; this one pins the
 * shape of the SQL and the guard rails.
 */
@DisplayName("Marking through a parent row")
class MarkingLinkedTablesTest {

  private static final MarkedTable ASSETS = new MarkedTable("assets");
  private static final MarkedTable AGENTS =
      MarkedTable.linkedTo("agents", "agent_asset", "assets", "asset_id");
  private static final MarkedTable JOBS =
      MarkedTable.linkedTo("asset_agent_jobs", "asset_agent_agent", "agents", "agent_id");
  private static final MarkedTable INJECTS_ASSETS =
      MarkedTable.linkedTo("injects_assets", "asset_id", "assets", "asset_id");

  private static final MarkedTable FINDINGS_ASSETS =
      MarkedTable.linkedTo("findings_assets", "asset_id", "assets", "asset_id");
  private static final MarkedTable FINDINGS =
      MarkedTable.throughLinkRows("findings", "finding_id", "findings_assets", "finding_id");

  private static final MarkedTables TABLES =
      new MarkedTables(
          Map.of(
              "assets", ASSETS,
              "agents", AGENTS,
              "asset_agent_jobs", JOBS,
              "injects_assets", INJECTS_ASSETS,
              "findings_assets", FINDINGS_ASSETS,
              "findings", FINDINGS));

  private final MarkingDimension dimension = new MarkingDimension(TABLES);
  private final ScopeStatementInspector inspector = new ScopeStatementInspector(List.of(dimension));

  private static String flatten(String sql) {
    return sql.replaceAll("\\s+", " ").trim();
  }

  private static final String AGENTS_PREDICATE =
      "(a.agent_asset IS NULL OR EXISTS (SELECT 1 FROM assets mkp0_assets"
          + " WHERE mkp0_assets.asset_id = a.agent_asset"
          + " AND is_marking_set_allowed(mkp0_assets.marking_ids)))";

  @Nested
  @DisplayName("predicate")
  class Predicate {

    @Test
    @DisplayName("asks whether the parent row the foreign key points to is allowed")
    void singleHop() {
      assertEquals(AGENTS_PREDICATE, dimension.readPredicate("agents", "a"));
    }

    @Test
    @DisplayName("keeps a row with no parent: a NULL foreign key is unmarked, hence visible")
    void nullForeignKeyIsVisible() {
      assertTrue(dimension.readPredicate("agents", "a").startsWith("(a.agent_asset IS NULL OR "));
    }

    @Test
    @DisplayName("resolves a chain hop by hop down to the one column that holds the marking")
    void chainResolvesToTheMarkingColumn() {
      String predicate = dimension.readPredicate("asset_agent_jobs", "j");
      assertEquals(
          "(j.asset_agent_agent IS NULL OR EXISTS (SELECT 1 FROM agents mkp0_agents"
              + " WHERE mkp0_agents.agent_id = j.asset_agent_agent"
              + " AND (mkp0_agents.agent_asset IS NULL OR EXISTS (SELECT 1 FROM assets mkp1_assets"
              + " WHERE mkp1_assets.asset_id = mkp0_agents.agent_asset"
              + " AND is_marking_set_allowed(mkp1_assets.marking_ids)))))",
          predicate);
    }

    @Test
    @DisplayName("uses the same predicate for reads and writes")
    void writeMatchesRead() {
      assertEquals(dimension.readPredicate("agents", "a"), dimension.writePredicate("agents", "a"));
    }

    @Test
    @DisplayName("an own-column table keeps its local test next to linked ones")
    void ownColumnTableUnchanged() {
      assertEquals("is_marking_set_allowed(x.marking_ids)", dimension.readPredicate("assets", "x"));
    }

    @Test
    @DisplayName("a linked table is covered and listed as active, so the inspector's gate fires")
    void linkedTableIsActive() {
      assertTrue(dimension.covers("agents"));
      assertTrue(
          dimension.activeTables().containsAll(Set.of("assets", "agents", "injects_assets")));
    }
  }

  private static final String FINDINGS_LINKS =
      " FROM findings_assets mkl0_findings_assets"
          + " WHERE mkl0_findings_assets.finding_id = f.finding_id";

  private static final String FINDINGS_PREDICATE =
      "(NOT EXISTS (SELECT 1"
          + FINDINGS_LINKS
          + ") OR EXISTS (SELECT 1"
          + FINDINGS_LINKS
          + " AND (mkl0_findings_assets.asset_id IS NULL OR EXISTS (SELECT 1 FROM assets mkp1_assets"
          + " WHERE mkp1_assets.asset_id = mkl0_findings_assets.asset_id"
          + " AND is_marking_set_allowed(mkp1_assets.marking_ids)))))";

  @Nested
  @DisplayName("through link rows (a finding and its findings_assets)")
  class ThroughLinkRows {

    @Test
    @DisplayName("is visible with no link, or with at least one link the reader may see")
    void noLinkOrAtLeastOneVisibleLink() {
      assertEquals(FINDINGS_PREDICATE, dimension.readPredicate("findings", "f"));
    }

    @Test
    @DisplayName("resolves each link through its own parent, down to the asset's marking column")
    void linksAreResolvedThroughTheirParent() {
      String predicate = dimension.readPredicate("findings", "f");
      assertTrue(predicate.contains("is_marking_set_allowed(mkp1_assets.marking_ids)"), predicate);
    }

    @Test
    @DisplayName("uses the same predicate for reads and writes")
    void writeMatchesRead() {
      assertEquals(
          dimension.readPredicate("findings", "f"), dimension.writePredicate("findings", "f"));
    }

    @Test
    @DisplayName("narrows the primary table through its WHERE")
    void primaryTable() {
      String out = flatten(inspector.inspect("SELECT f.* FROM findings f WHERE f.finding_id = ?"));
      assertTrue(out.contains("WHERE (f.finding_id = ?)"), out);
      assertTrue(out.contains("AND (" + FINDINGS_PREDICATE + ")"), out);
    }

    @Test
    @DisplayName("wraps the table when it is reached through a join")
    void joinedTable() {
      String out =
          flatten(
              inspector.inspect(
                  "SELECT * FROM other o JOIN findings f ON f.finding_id = o.finding_ref"));
      assertTrue(out.contains("JOIN (SELECT * FROM findings f WHERE " + FINDINGS_PREDICATE), out);
    }

    @Test
    @DisplayName("is active, so a statement on the table alone reaches the rewrite")
    void isActive() {
      assertTrue(dimension.covers("findings"));
      assertTrue(dimension.activeTables().contains("findings"));
    }

    @Test
    @DisplayName("names the columns to check against the schema")
    void columnsToCheck() {
      assertEquals(
          List.of(
              new MarkedTable.ColumnRef("findings", "finding_id"),
              new MarkedTable.ColumnRef("findings_assets", "finding_id")),
          FINDINGS.linkedColumns());
    }

    @Test
    @DisplayName("fails fast when the link table is not marked, rather than leaving findings open")
    void unmarkedLinkTableFailsFast() {
      IllegalArgumentException error =
          assertThrows(
              IllegalArgumentException.class,
              () -> new MarkedTables(Map.of("assets", ASSETS, "findings", FINDINGS)));
      assertTrue(error.getMessage().contains("findings_assets"), error.getMessage());
    }

    @Test
    @DisplayName("a table marked in more than one way is refused")
    void exactlyOneWayOfBeingMarked() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new MarkedTable(
                  "findings",
                  "marking_ids",
                  null,
                  new MarkedTable.LinkRows("finding_id", "findings_assets", "finding_id")));
    }
  }

  @Nested
  @DisplayName("inside the inspector")
  class InsideTheInspector {

    @Test
    @DisplayName("narrows the primary table through its WHERE")
    void primaryTable() {
      String out = flatten(inspector.inspect("SELECT a.* FROM agents a WHERE a.agent_id = ?"));
      assertTrue(out.contains("WHERE (a.agent_id = ?)"), out);
      // combineCall wraps the predicate in its own parentheses, so precedence survives an OR.
      assertTrue(out.contains("AND (" + AGENTS_PREDICATE + ")"), out);
    }

    @Test
    @DisplayName("wraps a linked table reached through a join")
    void joinedTable() {
      String out =
          flatten(
              inspector.inspect(
                  "SELECT * FROM other o JOIN agents a ON a.agent_id = o.agent_ref WHERE o.id = ?"));
      assertTrue(out.contains("JOIN (SELECT * FROM agents a WHERE " + AGENTS_PREDICATE), out);
    }

    @Test
    @DisplayName("filters a join table read on its own, with no asset joined")
    void joinTableAlone() {
      String out = flatten(inspector.inspect("SELECT ia.asset_id FROM injects_assets ia"));
      assertTrue(
          out.contains(
              "(ia.asset_id IS NULL OR EXISTS (SELECT 1 FROM assets mkp0_assets"
                  + " WHERE mkp0_assets.asset_id = ia.asset_id"
                  + " AND is_marking_set_allowed(mkp0_assets.marking_ids)))"),
          out);
    }

    @Test
    @DisplayName(
        "guards the DELETE of a join table, so a hidden link survives a collection rewrite")
    void guardsDelete() {
      String out = flatten(inspector.inspect("DELETE FROM injects_assets WHERE inject_id = ?"));
      assertTrue(out.contains("injects_assets.asset_id IS NULL OR EXISTS"), out);
      assertTrue(out.contains("is_marking_set_allowed(mkp0_assets.marking_ids)"), out);
    }

    @Test
    @DisplayName("guards the UPDATE of a linked table")
    void guardsUpdate() {
      String out =
          flatten(inspector.inspect("UPDATE agents SET agent_last_seen = ? WHERE agent_id = ?"));
      assertTrue(out.contains("agents.agent_asset IS NULL OR EXISTS"), out);
    }

    @Test
    @DisplayName("leaves a statement that touches no marked table byte-identical")
    void unrelatedStatementUntouched() {
      String sql = "SELECT * FROM other o WHERE o.id = ?";
      assertEquals(sql, inspector.inspect(sql));
    }

    @Test
    @DisplayName("filters the linked table inside a sub-query as well")
    void subQuery() {
      String out =
          flatten(
              inspector.inspect(
                  "SELECT * FROM other o WHERE o.id IN (SELECT a.agent_id FROM agents a)"));
      assertTrue(out.contains("EXISTS (SELECT 1 FROM assets mkp0_assets"), out);
    }
  }

  @Nested
  @DisplayName("startup checks")
  class StartupChecks {

    @Test
    @DisplayName("a link to a table that is not marked fails fast")
    void missingParentFailsFast() {
      IllegalArgumentException error =
          assertThrows(
              IllegalArgumentException.class, () -> new MarkedTables(Map.of("agents", AGENTS)));
      assertTrue(error.getMessage().contains("assets"), error.getMessage());
      assertTrue(error.getMessage().contains("not an active marked table"), error.getMessage());
    }

    @Test
    @DisplayName("a cycle between links fails fast")
    void cycleFailsFast() {
      MarkedTable a = MarkedTable.linkedTo("a_table", "b_ref", "b_table", "id");
      MarkedTable b = MarkedTable.linkedTo("b_table", "a_ref", "a_table", "id");
      IllegalArgumentException error =
          assertThrows(
              IllegalArgumentException.class,
              () -> new MarkedTables(Map.of("a_table", a, "b_table", b)));
      assertTrue(error.getMessage().contains("cycle"), error.getMessage());
    }

    @Test
    @DisplayName("withDerived refuses a derived table that is also active through its own column")
    void cannotDeriveAnOwnColumnTable() {
      MarkedTables own = new MarkedTables(Map.of("assets", ASSETS));
      MarkedTable rederive = MarkedTable.linkedTo("assets", "x", "assets", "asset_id");
      assertThrows(IllegalArgumentException.class, () -> own.withDerived(List.of(rederive)));
    }

    @Test
    @DisplayName("withDerived refuses a table listed twice")
    void cannotDeriveTwice() {
      MarkedTables own = new MarkedTables(Map.of("assets", ASSETS));
      assertThrows(IllegalArgumentException.class, () -> own.withDerived(List.of(AGENTS, AGENTS)));
    }

    @Test
    @DisplayName("withDerived refuses a table with its own marking column")
    void cannotDeriveAnUnlinkedTable() {
      MarkedTables own = new MarkedTables(Map.of("assets", ASSETS));
      assertThrows(
          IllegalArgumentException.class,
          () -> own.withDerived(List.of(new MarkedTable("documents"))));
    }

    @Test
    @DisplayName("withDerived adds a derived table on top of the active ones")
    void withDerivedAddsTheTable() {
      MarkedTables derived =
          new MarkedTables(Map.of("assets", ASSETS)).withDerived(List.of(AGENTS));
      assertEquals(Set.of("assets", "agents"), derived.tableNames());
      assertTrue(derived.get("agents").isLinked());
    }

    @Test
    @DisplayName("withDerived resolves a chain whatever the order of the entries")
    void withDerivedResolvesChainsInAnyOrder() {
      MarkedTables derived =
          new MarkedTables(Map.of("assets", ASSETS)).withDerived(List.of(JOBS, AGENTS));
      assertEquals(Set.of("assets", "agents", "asset_agent_jobs"), derived.tableNames());
    }

    @Test
    @DisplayName("a derived table whose marked table is not active stays unfiltered")
    void inactiveMarkedTableLeavesDerivedTablesInert() {
      MarkedTables noAssets =
          new MarkedTables(Map.of("documents", new MarkedTable("documents")))
              .withDerived(List.of(AGENTS, JOBS));
      assertEquals(Set.of("documents"), noAssets.tableNames());
    }

    @Test
    @DisplayName("identifiers that are not plain names are rejected, they end up verbatim in SQL")
    void identifiersAreValidated() {
      assertThrows(
          IllegalArgumentException.class,
          () -> MarkedTable.linkedTo("agents", "agent_asset; DROP TABLE x", "assets", "asset_id"));
      assertThrows(
          IllegalArgumentException.class,
          () -> MarkedTable.linkedTo("agents", "agent_asset", "assets)", "asset_id"));
    }

    @Test
    @DisplayName("a linked table cannot also carry a marking column")
    void linkedTableHasNoColumn() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new MarkedTable(
                  "agents",
                  "marking_ids",
                  new MarkedTable.ParentLink("agent_asset", "assets", "asset_id")));
    }
  }

  @Nested
  @DisplayName("derived tables registry")
  class Registry {

    private final MarkedTables fromSchema =
        new MarkedTables(
            Map.of(
                "assets", ASSETS, "marking_definitions", new MarkedTable("marking_definitions")));

    @Test
    @DisplayName("activating assets filters every table derived from it")
    void activatingAssetsActivatesItsDerivedTables() {
      MarkedTables active =
          fromSchema.restrictTo(List.of("assets")).withDerived(MarkingDerivedTables.ALL);
      assertEquals(
          Set.of(
              "assets",
              "injects_assets",
              "asset_groups_assets",
              "findings_assets",
              "findings",
              "injects_expectations",
              "agents",
              "execution_traces"),
          active.tableNames());
    }

    @Test
    @DisplayName("with assets inactive, no derived table is filtered")
    void derivedTablesFollowAssets() {
      MarkedTables active =
          fromSchema
              .restrictTo(List.of("marking_definitions"))
              .withDerived(MarkingDerivedTables.ALL);
      assertEquals(Set.of("marking_definitions"), active.tableNames());
    }

    @Test
    @DisplayName("a derived table cannot be listed in active-tables: it has no marking column")
    void derivedTableIsNotAnActiveTable() {
      IllegalArgumentException error =
          assertThrows(
              IllegalArgumentException.class,
              () -> fromSchema.restrictTo(List.of("assets", "findings")));
      assertTrue(error.getMessage().contains("findings"), error.getMessage());
    }

    @Test
    @DisplayName("an expectation follows the asset it was computed on")
    void expectationPredicate() {
      MarkingDimension registry =
          new MarkingDimension(
              fromSchema.restrictTo(List.of("assets")).withDerived(MarkingDerivedTables.ALL));
      assertEquals(
          "(ie.asset_id IS NULL OR EXISTS (SELECT 1 FROM assets mkp0_assets"
              + " WHERE mkp0_assets.asset_id = ie.asset_id"
              + " AND is_marking_set_allowed(mkp0_assets.marking_ids)))",
          registry.readPredicate("injects_expectations", "ie"));
    }

    @Test
    @DisplayName("a finding is filtered through its findings_assets rows")
    void findingPredicate() {
      MarkingDimension registry =
          new MarkingDimension(
              fromSchema.restrictTo(List.of("assets")).withDerived(MarkingDerivedTables.ALL));
      assertEquals(FINDINGS_PREDICATE, registry.readPredicate("findings", "f"));
    }

    @Test
    @DisplayName("a trace follows the asset of the agent that produced it")
    void tracePredicate() {
      MarkingDimension registry =
          new MarkingDimension(
              fromSchema.restrictTo(List.of("assets")).withDerived(MarkingDerivedTables.ALL));
      assertEquals(
          "(t.execution_agent_id IS NULL OR EXISTS (SELECT 1 FROM agents mkp0_agents"
              + " WHERE mkp0_agents.agent_id = t.execution_agent_id"
              + " AND (mkp0_agents.agent_asset IS NULL OR EXISTS (SELECT 1 FROM assets mkp1_assets"
              + " WHERE mkp1_assets.asset_id = mkp0_agents.agent_asset"
              + " AND is_marking_set_allowed(mkp1_assets.marking_ids)))))",
          registry.readPredicate("execution_traces", "t"));
    }

    @Test
    @DisplayName("is case-insensitive like the rest of the table matching")
    void caseInsensitive() {
      assertFalse(TABLES.get("AGENTS") == null);
    }
  }
}
