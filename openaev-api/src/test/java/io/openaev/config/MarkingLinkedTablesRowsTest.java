package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the SQL the {@link MarkingDimension} produces for a table marked <b>through a parent</b>
 * against real rows in Postgres: a child is visible when it points to no parent or to a parent the
 * reader may see, hidden otherwise, and a chain (job → agent → asset) resolves all the way down.
 *
 * <p>Like {@code MarkingRewriteHypothesisTest}, it depends on nothing but the inspector and {@code
 * is_marking_set_allowed}: the fixture tables are temporary and the clearance is written straight
 * into the GUC.
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@Transactional
@DisplayName("Marking through a parent row — on real rows")
class MarkingLinkedTablesRowsTest {

  private final ScopeStatementInspector inspector =
      new ScopeStatementInspector(
          List.of(
              new MarkingDimension(
                  new MarkedTables(
                      Map.of(
                          "mk_assets", new MarkedTable("mk_assets"),
                          "mk_agents",
                              MarkedTable.linkedTo(
                                  "mk_agents", "agent_asset", "mk_assets", "asset_id"),
                          "mk_jobs",
                              MarkedTable.linkedTo("mk_jobs", "job_agent", "mk_agents", "agent_id"),
                          "mk_finding_assets",
                              MarkedTable.linkedTo(
                                  "mk_finding_assets", "asset_id", "mk_assets", "asset_id"),
                          "mk_findings",
                              MarkedTable.throughLinkRows(
                                  "mk_findings",
                                  "finding_id",
                                  "mk_finding_assets",
                                  "finding_id"))))));

  @Autowired private EntityManager entityManager;

  @BeforeEach
  void seed() {
    execute(
        """
        CREATE TEMPORARY TABLE mk_assets (
          asset_id    text PRIMARY KEY,
          marking_ids text[]) ON COMMIT DROP;
        """);
    execute(
        """
        INSERT INTO mk_assets (asset_id, marking_ids) VALUES
          ('a_open',  NULL),
          ('a_green', '{tlp_green}'),
          ('a_red',   '{tlp_red}');
        """);
    // agent_asset is nullable: an agent attached to no asset has no marking to inherit.
    execute(
        """
        CREATE TEMPORARY TABLE mk_agents (
          agent_id    text PRIMARY KEY,
          agent_asset text REFERENCES mk_assets (asset_id)) ON COMMIT DROP;
        """);
    execute(
        """
        INSERT INTO mk_agents (agent_id, agent_asset) VALUES
          ('ag_open',   'a_open'),
          ('ag_green',  'a_green'),
          ('ag_red',    'a_red'),
          ('ag_nobody', NULL);
        """);
    execute(
        """
        CREATE TEMPORARY TABLE mk_jobs (
          job_id    text PRIMARY KEY,
          job_agent text REFERENCES mk_agents (agent_id)) ON COMMIT DROP;
        """);
    execute(
        """
        INSERT INTO mk_jobs (job_id, job_agent) VALUES
          ('j_open',  'ag_open'),
          ('j_green', 'ag_green'),
          ('j_red',   'ag_red'),
          ('j_none',  NULL);
        """);
  }

  /**
   * Findings and their links to assets: a finding has no single parent, so it is marked through its
   * link rows. {@code f_mixed} sits on a green and a red asset, {@code f_red_open} on a red and an
   * unmarked one, {@code f_none} on nothing.
   */
  private void seedFindings() {
    execute(
        """
        CREATE TEMPORARY TABLE mk_findings (
          finding_id text PRIMARY KEY) ON COMMIT DROP;
        """);
    execute(
        """
        INSERT INTO mk_findings (finding_id) VALUES
          ('f_none'), ('f_open'), ('f_green'), ('f_red'), ('f_mixed'), ('f_red_open');
        """);
    execute(
        """
        CREATE TEMPORARY TABLE mk_finding_assets (
          finding_id text NOT NULL REFERENCES mk_findings (finding_id),
          asset_id   text NOT NULL REFERENCES mk_assets (asset_id),
          PRIMARY KEY (finding_id, asset_id)) ON COMMIT DROP;
        """);
    execute(
        """
        INSERT INTO mk_finding_assets (finding_id, asset_id) VALUES
          ('f_open',     'a_open'),
          ('f_green',    'a_green'),
          ('f_red',      'a_red'),
          ('f_mixed',    'a_green'),
          ('f_mixed',    'a_red'),
          ('f_red_open', 'a_red'),
          ('f_red_open', 'a_open');
        """);
  }

  private List<String> visibleFindings() {
    return visible("SELECT f.finding_id FROM mk_findings f ORDER BY f.finding_id");
  }

  @Test
  @DisplayName("a finding is visible when at least one of its assets is, hidden when none is")
  void findingNeedsOneVisibleAsset() {
    seedFindings();
    setClearance("tlp_green");
    // f_red only has a hidden asset; f_mixed and f_red_open keep one visible asset each.
    assertEquals(
        List.of("f_green", "f_mixed", "f_none", "f_open", "f_red_open"), visibleFindings());
  }

  @Test
  @DisplayName("with no clearance a finding survives only through an unmarked asset or no asset")
  void findingWithoutClearance() {
    seedFindings();
    setClearance("");
    assertEquals(List.of("f_none", "f_open", "f_red_open"), visibleFindings());
  }

  @Test
  @DisplayName("a finding attached to no asset is visible: nothing marks it")
  void findingWithoutAssetIsVisible() {
    seedFindings();
    setClearance("");
    assertEquals(true, visibleFindings().contains("f_none"));
  }

  @Test
  @DisplayName("a clearance covering every marking shows every finding")
  void fullClearanceShowsEveryFinding() {
    seedFindings();
    setClearance("tlp_green,tlp_red");
    assertEquals(
        List.of("f_green", "f_mixed", "f_none", "f_open", "f_red", "f_red_open"),
        visibleFindings());
  }

  @Test
  @DisplayName("the link rows of a visible finding still hide the restricted asset")
  void linkRowsStayFiltered() {
    seedFindings();
    setClearance("tlp_green");
    assertEquals(
        List.of("a_green"),
        visible(
            "SELECT l.asset_id FROM mk_finding_assets l WHERE l.finding_id = 'f_mixed'"
                + " ORDER BY l.asset_id"));
  }

  @Test
  @DisplayName("re-marking an asset changes which findings show, with nothing copied")
  void findingFollowsItsAssets() {
    seedFindings();
    setClearance("tlp_green");
    assertEquals(true, visibleFindings().contains("f_mixed"));

    execute("UPDATE mk_assets SET marking_ids = '{tlp_red}' WHERE asset_id = 'a_green'");
    // f_mixed now only has red assets left, and f_green only a red one.
    assertEquals(List.of("f_none", "f_open", "f_red_open"), visibleFindings());
  }

  private void execute(String sql) {
    entityManager.createNativeQuery(sql).executeUpdate();
  }

  private void setClearance(String clearance) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_markings', :scope, true)")
        .setParameter("scope", clearance)
        .getSingleResult();
  }

  @SuppressWarnings("unchecked")
  private List<String> visible(String sql) {
    return entityManager.createNativeQuery(inspector.inspect(sql)).getResultList();
  }

  private List<String> visibleAgents() {
    return visible("SELECT a.agent_id FROM mk_agents a ORDER BY a.agent_id");
  }

  private List<String> visibleJobs() {
    return visible("SELECT j.job_id FROM mk_jobs j ORDER BY j.job_id");
  }

  @Test
  @DisplayName("an agent follows the marking of the asset it points to")
  void childFollowsItsParent() {
    setClearance("tlp_green");
    assertEquals(List.of("ag_green", "ag_nobody", "ag_open"), visibleAgents());
  }

  @Test
  @DisplayName("with no clearance only the unmarked parents' children and the parentless remain")
  void noClearanceHidesMarkedParents() {
    setClearance("");
    assertEquals(List.of("ag_nobody", "ag_open"), visibleAgents());
  }

  @Test
  @DisplayName("a clearance covering every marking shows every child")
  void fullClearanceShowsAll() {
    setClearance("tlp_green,tlp_red");
    assertEquals(List.of("ag_green", "ag_nobody", "ag_open", "ag_red"), visibleAgents());
  }

  @Test
  @DisplayName("a row pointing to no parent is visible: no parent, no marking")
  void parentlessRowIsVisible() {
    setClearance("");
    assertEquals(List.of("ag_nobody", "ag_open"), visibleAgents());
    assertEquals(List.of("j_none", "j_open"), visibleJobs());
  }

  @Test
  @DisplayName("a chain resolves down to the asset: a job inherits through its agent")
  void chainResolvesToTheAsset() {
    setClearance("tlp_green");
    assertEquals(List.of("j_green", "j_none", "j_open"), visibleJobs());

    setClearance("tlp_green,tlp_red");
    assertEquals(List.of("j_green", "j_none", "j_open", "j_red"), visibleJobs());
  }

  @Test
  @DisplayName("changing the asset's marking changes what its children show, with nothing copied")
  void noCopyToKeepInSync() {
    setClearance("tlp_green");
    assertEquals(List.of("ag_green", "ag_nobody", "ag_open"), visibleAgents());

    execute("UPDATE mk_assets SET marking_ids = '{tlp_red}' WHERE asset_id = 'a_green'");
    assertEquals(List.of("ag_nobody", "ag_open"), visibleAgents());
  }

  @Test
  @DisplayName("a join through the linked table does not bring a hidden child back")
  void joinedChildStaysHidden() {
    setClearance("tlp_green");
    assertEquals(
        List.of("j_green", "j_open"),
        visible(
            "SELECT j.job_id FROM mk_jobs j JOIN mk_agents a ON a.agent_id = j.job_agent"
                + " ORDER BY j.job_id"));
  }

  @Test
  @DisplayName("a DELETE only reaches the rows the reader can see")
  void deleteLeavesHiddenRowsAlone() {
    setClearance("tlp_green");
    // Jobs are the leaf of the chain, so deleting them breaks no foreign key.
    entityManager.createNativeQuery(inspector.inspect("DELETE FROM mk_jobs")).executeUpdate();

    // Read back without the rewrite: only the job attached, through its agent, to the red asset
    // is out of the reader's reach, so it is the only one left.
    @SuppressWarnings("unchecked")
    List<String> remaining =
        entityManager
            .createNativeQuery("SELECT job_id FROM mk_jobs ORDER BY job_id")
            .getResultList();
    assertEquals(List.of("j_red"), remaining);
  }
}
