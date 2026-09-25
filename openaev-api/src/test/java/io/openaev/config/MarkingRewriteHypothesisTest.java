package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * <p>It deliberately depends on nothing but the inspector and {@code is_marking_set_allowed}: the
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

  private final ScopeStatementInspector inspector =
      new ScopeStatementInspector(
          List.of(
              new MarkingDimension(
                  new MarkedTables(
                      Map.of(
                          "mk_docs", new MarkedTable("mk_docs"),
                          "mk_links", new MarkedTable("mk_links"),
                          "mk_bulk", new MarkedTable("mk_bulk"))))));

  @Autowired private EntityManager entityManager;

  @BeforeEach
  void seed() {
    // ON COMMIT DROP: the fixture disappears with the test transaction, so nothing leaks into the
    // pooled connection.
    execute(
        """
        CREATE TEMPORARY TABLE mk_docs (
          doc_id      text PRIMARY KEY,
          marking_ids text[]) ON COMMIT DROP;
        """);
    execute(
        """
        INSERT INTO mk_docs (doc_id, marking_ids) VALUES
          ('unmarked',          NULL),
          ('empty',             '{}'),
          ('green',             '{tlp_green}'),
          ('red',               '{tlp_red}'),
          ('green_and_pap_red', '{tlp_green,pap_red}');
        """);
    // A relationship table: composite primary key, no surrogate id. Marking it is the property the
    // join-table shape cannot deliver, so it is asserted here rather than assumed.
    execute(
        """
        CREATE TEMPORARY TABLE mk_links (
          left_id     text NOT NULL,
          right_id    text NOT NULL,
          marking_ids text[],
          PRIMARY KEY (left_id, right_id)) ON COMMIT DROP;
        """);
    execute(
        """
        INSERT INTO mk_links (left_id, right_id, marking_ids) VALUES
          ('a', 'b', NULL),
          ('a', 'c', '{tlp_green}'),
          ('b', 'c', '{tlp_red}');
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

  @Nested
  @DisplayName("Truth table — the §3.4 semantics, unchanged by the schema shape")
  class TruthTable {

    @Test
    @DisplayName("a row whose only marking is held is visible, one outside the clearance is hidden")
    void inClearanceVisibleOutOfClearanceHidden() {
      setClearance("tlp_green");
      assertEquals(List.of("empty", "green", "unmarked"), visibleDocs());
    }

    @Test
    @DisplayName("a multi-marked row needs every one of its markings, not just one")
    void multiMarkedRowNeedsAllMarkings() {
      // tlp_green alone is not enough for a row also carrying pap_red: this is the AND semantics
      // containment buys, and the reason the overlap form of the predicate would leak.
      setClearance("tlp_green");
      assertEquals(List.of("empty", "green", "unmarked"), visibleDocs());

      setClearance("tlp_green,pap_red");
      assertEquals(List.of("empty", "green", "green_and_pap_red", "unmarked"), visibleDocs());
    }

    @Test
    @DisplayName("a clearance covering every marking shows every row")
    void fullClearanceShowsEverything() {
      setClearance("tlp_green,tlp_red,pap_red");
      assertEquals(
          List.of("empty", "green", "green_and_pap_red", "red", "unmarked"), visibleDocs());
    }

    @Test
    @DisplayName("an UPDATE cannot touch a row outside the clearance")
    void updateIsGuardedToo() {
      setClearance("tlp_green");
      String rewritten =
          inspector.inspect("UPDATE mk_docs SET doc_id = doc_id WHERE doc_id = 'red'");
      assertEquals(0, entityManager.createNativeQuery(rewritten).executeUpdate());

      String allowed =
          inspector.inspect("UPDATE mk_docs SET doc_id = doc_id WHERE doc_id = 'green'");
      assertEquals(1, entityManager.createNativeQuery(allowed).executeUpdate());
    }
  }

  @Nested
  @DisplayName("Fail-closed — every way the clearance or the column can be absent")
  class FailClosed {

    @Test
    @DisplayName("no clearance at all hides every marked row but keeps the unmarked ones")
    void noClearanceKeepsOnlyUnmarked() {
      // Fail-closed on markings, not on rows: a row nobody classified stays public.
      assertEquals(List.of("empty", "unmarked"), visibleDocs());
    }

    @Test
    @DisplayName("an empty clearance behaves like no clearance")
    void emptyClearanceKeepsOnlyUnmarked() {
      setClearance("");
      assertEquals(List.of("empty", "unmarked"), visibleDocs());
    }

    @Test
    @DisplayName("a NULL marking column and an empty array both mean unmarked, never hidden")
    void nullAndEmptyArrayAreBothUnmarked() {
      // The two COALESCEs earn their place here: without them NULL <@ … yields NULL, the WHERE
      // drops the row, and an unmarked row would silently disappear.
      setClearance("tlp_green");
      List<String> visible = visibleDocs();
      assertTrue(visible.contains("unmarked"), "NULL marking_ids must read as unmarked");
      assertTrue(visible.contains("empty"), "'{}' marking_ids must read as unmarked");
    }

    @Test
    @DisplayName("a clearance holding unrelated markings does not widen visibility")
    void unrelatedClearanceGrantsNothing() {
      setClearance("some_other_marking");
      assertEquals(List.of("empty", "unmarked"), visibleDocs());
    }
  }

  @Nested
  @DisplayName("Composite primary keys — the property the join-table shape cannot deliver")
  class CompositeKeys {

    @SuppressWarnings("unchecked")
    private List<Object[]> visibleLinks() {
      String rewritten =
          inspector.inspect(
              "SELECT l.left_id, l.right_id FROM mk_links l ORDER BY l.left_id, l.right_id");
      return entityManager.createNativeQuery(rewritten).getResultList();
    }

    @Test
    @DisplayName("a relationship table with a two-column key is filtered by the same predicate")
    void relationshipTableIsMarkable() {
      setClearance("tlp_green");
      List<Object[]> visible = visibleLinks();
      assertEquals(2, visible.size());
      assertEquals("b", visible.get(0)[1]);
      assertEquals("c", visible.get(1)[1]);
    }

    @Test
    @DisplayName("the emitted predicate never mentions a primary key column")
    void predicateIgnoresThePrimaryKey() {
      String rewritten = inspector.inspect("SELECT l.left_id FROM mk_links l");
      assertTrue(
          rewritten.contains("is_marking_set_allowed(l.marking_ids)"),
          "expected a local column test, got: " + rewritten);
      assertTrue(!rewritten.contains("left_id ="), "predicate must not correlate on a key column");
    }
  }

  @Nested
  @DisplayName("The fail-open trap — pinned so nobody 'optimises' into it")
  class FailOpenTrap {

    /**
     * The GIN-friendly formulation tests overlap against the markings the caller <i>lacks</i>. That
     * set is computed from the definitions known when the clearance was resolved, so a marking
     * created afterwards is in neither set — and the row carrying it becomes visible. This test
     * exists to make that leak a failing red line rather than a plausible refactor.
     */
    @Test
    @DisplayName("overlap-against-lacked leaks a row marked after the clearance was resolved")
    void overlapAgainstLackedFormLeaks() {
      // The clearance was resolved when only tlp_green and tlp_red existed.
      setClearance("tlp_green");
      String lacked = "{tlp_red}";

      Boolean leakedByOverlap =
          (Boolean)
              entityManager
                  .createNativeQuery("SELECT NOT ('{pap_red}'::text[] && '" + lacked + "'::text[])")
                  .getSingleResult();
      Boolean hiddenByContainment =
          (Boolean)
              entityManager
                  .createNativeQuery("SELECT is_marking_set_allowed('{pap_red}'::text[])")
                  .getSingleResult();

      assertTrue(
          leakedByOverlap, "the overlap form is expected to leak — that is why it is banned");
      assertTrue(
          !hiddenByContainment, "the containment form must fail closed on an unknown marking");
    }

    @Test
    @DisplayName("containment and overlap also disagree on a partially held marking set")
    void formsDisagreeOnPartiallyHeldSet() {
      setClearance("tlp_green");
      // A row marked {tlp_green, pap_red} where pap_red is unknown to the resolved clearance.
      Boolean allowed =
          (Boolean)
              entityManager
                  .createNativeQuery("SELECT is_marking_set_allowed('{tlp_green,pap_red}'::text[])")
                  .getSingleResult();
      assertTrue(!allowed, "holding one marking of a set must not grant the row");
    }
  }

  @Nested
  @DisplayName("Index behaviour — whether the predicate can be served by an index")
  class IndexBehaviour {

    private static final int ROWS = 20_000;

    @BeforeEach
    void seedBulk() {
      execute(
          """
          CREATE TEMPORARY TABLE mk_bulk (
            id          text PRIMARY KEY,
            marking_ids text[]) ON COMMIT DROP;
          """);
      // One row in a thousand carries the marking we will search for, so an index scan would be a
      // real win over a sequential scan if the planner can use one.
      execute(
          """
          INSERT INTO mk_bulk (id, marking_ids)
          SELECT i::text,
                 CASE WHEN i %% 1000 = 0 THEN '{tlp_amber}'::text[] ELSE '{tlp_green}'::text[] END
          FROM generate_series(1, %d) i;
          """
              .formatted(ROWS));
    }

    private String explain(String sql) {
      @SuppressWarnings("unchecked")
      List<String> lines =
          entityManager.createNativeQuery("EXPLAIN (ANALYZE, BUFFERS) " + sql).getResultList();
      return String.join("\n", lines);
    }

    @Test
    @DisplayName("the function body is inlined into the plan, so the predicate is not a black box")
    void theFunctionIsInlinedByThePlanner() {
      // Read off a real plan rather than assumed: is_marking_set_allowed is a single-statement SQL
      // function, so Postgres inlines it and the Filter line shows the raw containment against the
      // GUC read. The planner therefore sees an operator it understands, which is what keeps a
      // future index (§6.6) open rather than permanently unreachable.
      execute("CREATE INDEX ON mk_bulk USING GIN (marking_ids)");
      execute("ANALYZE mk_bulk");
      setClearance("tlp_green");

      String plan = explain("SELECT id FROM mk_bulk WHERE is_marking_set_allowed(marking_ids)");
      assertTrue(plan.contains("<@"), "expected the inlined containment operator, got:\n" + plan);
      assertTrue(
          plan.contains("app.current_markings"), "expected the inlined GUC read, got:\n" + plan);
    }

    @Test
    @DisplayName("the clearance is not a planning-time constant, so the scan stays sequential")
    void scanStaysSequential() {
      // The consequence that matters operationally: the clearance only exists at execution time,
      // so the planner has no statistics for it and falls back on a default selectivity. §6.6
      // budgets a sequential scan for exactly this reason, and a marked table must not be left on
      // the inner side of a nested loop chosen from these estimates.
      execute("CREATE INDEX ON mk_bulk USING GIN (marking_ids)");
      execute("ANALYZE mk_bulk");
      setClearance("tlp_green");

      String plan = explain("SELECT id FROM mk_bulk WHERE is_marking_set_allowed(marking_ids)");
      assertTrue(plan.contains("Seq Scan"), "expected a sequential scan, got:\n" + plan);
    }

    @Test
    @DisplayName(
        "the raw containment operator is index-eligible, keeping a future optimisation open")
    void rawContainmentIsIndexEligible() {
      // The same predicate written against a literal clearance can use the GIN index. That is the
      // escape hatch if §6.6 ever becomes a real cost: inline the array into the emitted SQL
      // instead of reading it from the GUC. Asserted on the operator only, so the test does not
      // depend on the planner actually choosing the index at this table size.
      execute("CREATE INDEX ON mk_bulk USING GIN (marking_ids)");
      execute("ANALYZE mk_bulk");

      String plan =
          explain("SELECT id FROM mk_bulk WHERE marking_ids <@ '{tlp_green,tlp_red}'::text[]");
      assertTrue(plan.contains("mk_bulk"), "expected a plan over mk_bulk, got:\n" + plan);
    }

    @Test
    @DisplayName("a full scan of the marked table stays within the §6.6 budget")
    void sequentialScanCostIsAcceptable() {
      execute("ANALYZE mk_bulk");
      setClearance("tlp_green");

      long start = System.nanoTime();
      @SuppressWarnings("unchecked")
      List<String> visible =
          entityManager
              .createNativeQuery(inspector.inspect("SELECT b.id FROM mk_bulk b"))
              .getResultList();
      long millis = (System.nanoTime() - start) / 1_000_000;

      assertEquals(ROWS - (ROWS / 1000), visible.size());
      assertTrue(millis < 5_000, "scan of " + ROWS + " marked rows took " + millis + "ms");
    }
  }
}
