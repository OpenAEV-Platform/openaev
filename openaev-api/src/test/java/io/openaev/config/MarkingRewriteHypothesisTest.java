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
 * The go/no-go experiment for marking isolation by statement rewrite: takes the SQL the {@link
 * MarkingDimension} actually produces, runs it against real rows in Postgres, and checks the four
 * behaviours the whole design rests on — an unmarked row is visible to everyone, a row inside the
 * clearance is visible, a row outside it is hidden, and a row carrying several markings needs
 * <b>all</b> of them.
 *
 * <p>It deliberately depends on nothing but the inspector and {@code is_marking_missing}: the
 * fixture tables are temporary and the clearance is written straight into the GUC, so the
 * hypothesis is proven before any marking entity, resolver or product code exists.
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@Transactional
@DisplayName("Marking rewrite — hypothesis on real rows")
class MarkingRewriteHypothesisTest {

  private static final MarkedTable FIXTURE =
      new MarkedTable("mk_docs", "doc_id", "mk_docs_markings", "doc_id");

  private final ScopeStatementInspector inspector =
      new ScopeStatementInspector(
          List.of(new MarkingDimension(new MarkedTables(Map.of("mk_docs", FIXTURE)))));

  @Autowired private EntityManager entityManager;

  @BeforeEach
  void seed() {
    // ON COMMIT DROP: the fixture disappears with the test transaction, so nothing leaks into the
    // pooled connection.
    execute(
        """
        CREATE TEMPORARY TABLE mk_docs (doc_id text PRIMARY KEY) ON COMMIT DROP;
        """);
    execute(
        """
        CREATE TEMPORARY TABLE mk_docs_markings (
          doc_id     text NOT NULL,
          marking_id text NOT NULL,
          PRIMARY KEY (doc_id, marking_id)) ON COMMIT DROP;
        """);
    execute(
        """
        INSERT INTO mk_docs (doc_id)
        VALUES ('unmarked'), ('green'), ('red'), ('green_and_pap_red');
        """);
    execute(
        """
        INSERT INTO mk_docs_markings (doc_id, marking_id) VALUES
          ('green', 'tlp_green'),
          ('red', 'tlp_red'),
          ('green_and_pap_red', 'tlp_green'),
          ('green_and_pap_red', 'pap_red');
        """);
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

  /** Runs the rewritten form of a plain read, so the assertion is on SQL the inspector produced. */
  @SuppressWarnings("unchecked")
  private List<String> visibleDocs() {
    String rewritten = inspector.inspect("SELECT d.doc_id FROM mk_docs d ORDER BY d.doc_id");
    return entityManager.createNativeQuery(rewritten).getResultList();
  }

  @Test
  @DisplayName("no clearance hides every marked row but keeps the unmarked one")
  void noClearanceKeepsOnlyUnmarked() {
    // Fail-closed on markings, not on rows: a row nobody classified stays public.
    assertEquals(List.of("unmarked"), visibleDocs());
  }

  @Test
  @DisplayName("an empty clearance behaves like no clearance")
  void emptyClearanceKeepsOnlyUnmarked() {
    setClearance("");
    assertEquals(List.of("unmarked"), visibleDocs());
  }

  @Test
  @DisplayName("a row whose only marking is held is visible, one outside the clearance is hidden")
  void inClearanceVisibleOutOfClearanceHidden() {
    setClearance("tlp_green");
    assertEquals(List.of("green", "unmarked"), visibleDocs());
  }

  @Test
  @DisplayName("a multi-marked row needs every one of its markings, not just one")
  void multiMarkedRowNeedsAllMarkings() {
    // tlp_green alone is not enough for a row also carrying pap_red: this is the AND semantics the
    // anti-join buys, and the reason the positive form of the predicate would leak.
    setClearance("tlp_green");
    assertEquals(List.of("green", "unmarked"), visibleDocs());

    setClearance("tlp_green,pap_red");
    assertEquals(List.of("green", "green_and_pap_red", "unmarked"), visibleDocs());
  }

  @Test
  @DisplayName("a clearance covering every marking shows every row")
  void fullClearanceShowsEverything() {
    setClearance("tlp_green,tlp_red,pap_red");
    assertEquals(List.of("green", "green_and_pap_red", "red", "unmarked"), visibleDocs());
  }

  @Test
  @DisplayName("an UPDATE cannot touch a row outside the clearance")
  void updateIsGuardedToo() {
    setClearance("tlp_green");
    String rewritten = inspector.inspect("UPDATE mk_docs SET doc_id = doc_id WHERE doc_id = 'red'");
    assertEquals(0, entityManager.createNativeQuery(rewritten).executeUpdate());

    String allowed = inspector.inspect("UPDATE mk_docs SET doc_id = doc_id WHERE doc_id = 'green'");
    assertEquals(1, entityManager.createNativeQuery(allowed).executeUpdate());
  }
}
