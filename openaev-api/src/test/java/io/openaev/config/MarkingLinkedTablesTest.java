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
 * Covers marking a table <i>through a parent row</i> (Solution B of the task 4 design): the
 * predicate it emits, how the inspector applies it on every statement shape, and the startup checks
 * that keep a mis-wired link from leaving a table silently unprotected.
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
    @DisplayName("parses table.key<link.fk, and keeps the other arrow for a parent")
    void configurationArrow() {
      assertEquals(
          List.of(FINDINGS, FINDINGS_ASSETS),
          MarkingFilteringConfig.parseLinkedTables(
              List.of(
                  "findings.finding_id<findings_assets.finding_id",
                  "findings_assets.asset_id>assets.asset_id")));
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
      assertTrue(error.getMessage().contains("active-tables"), error.getMessage());
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
    @DisplayName("withLinked refuses to link a table that already has its own marking column")
    void cannotLinkAnOwnColumnTable() {
      MarkedTables own = new MarkedTables(Map.of("assets", ASSETS));
      MarkedTable relink = MarkedTable.linkedTo("assets", "x", "assets", "asset_id");
      assertThrows(IllegalArgumentException.class, () -> own.withLinked(List.of(relink)));
    }

    @Test
    @DisplayName("withLinked refuses a table listed twice")
    void cannotLinkTwice() {
      MarkedTables own = new MarkedTables(Map.of("assets", ASSETS));
      assertThrows(IllegalArgumentException.class, () -> own.withLinked(List.of(AGENTS, AGENTS)));
    }

    @Test
    @DisplayName("withLinked adds the link on top of the activated tables")
    void withLinkedAddsTheLink() {
      MarkedTables linked = new MarkedTables(Map.of("assets", ASSETS)).withLinked(List.of(AGENTS));
      assertEquals(Set.of("assets", "agents"), linked.tableNames());
      assertTrue(linked.get("agents").isLinked());
    }

    @Test
    @DisplayName("an inactive parent is refused rather than leaving the child unprotected")
    void inactiveParentIsRefused() {
      MarkedTables noAssets = new MarkedTables(Map.of("documents", new MarkedTable("documents")));
      assertThrows(IllegalArgumentException.class, () -> noAssets.withLinked(List.of(AGENTS)));
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
  @DisplayName("configuration")
  class Configuration {

    @Test
    @DisplayName("parses child.fk>parent.key entries")
    void parsesEntries() {
      assertEquals(
          List.of(AGENTS, INJECTS_ASSETS),
          MarkingFilteringConfig.parseLinkedTables(
              List.of(
                  "agents.agent_asset>assets.asset_id",
                  " injects_assets.asset_id>assets.asset_id ")));
    }

    @Test
    @DisplayName("ignores blank entries, so an empty property is inert")
    void ignoresBlankEntries() {
      assertTrue(MarkingFilteringConfig.parseLinkedTables(List.of("", "  ")).isEmpty());
      assertTrue(MarkingFilteringConfig.parseLinkedTables(List.of()).isEmpty());
    }

    @Test
    @DisplayName("a malformed entry fails the startup instead of being skipped")
    void malformedEntryFails() {
      for (String bad :
          List.of(
              "agents.agent_asset",
              "agents>assets.asset_id",
              "agents.agent_asset>assets",
              "agents.agent_asset > assets.asset_id",
              "agents.agent_asset>assets.asset_id;x")) {
        assertThrows(
            IllegalArgumentException.class,
            () -> MarkingFilteringConfig.parseLinkedTables(List.of(bad)),
            bad);
      }
    }

    @Test
    @DisplayName("is case-insensitive like the rest of the table matching")
    void caseInsensitive() {
      assertFalse(TABLES.get("AGENTS") == null);
    }
  }
}
