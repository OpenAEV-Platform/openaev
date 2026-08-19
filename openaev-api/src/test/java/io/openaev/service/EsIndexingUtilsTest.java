package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.engine.model.EsBase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for the shared incremental-indexing helpers used by both engine implementations
 * (Elasticsearch and OpenSearch): LIMIT-boundary-safe cursor advancement and deterministic (poison)
 * bulk error classification.
 */
@DisplayName("EsIndexingUtils cursor and poison-error helpers")
class EsIndexingUtilsTest {

  private static final Logger LOG = LoggerFactory.getLogger(EsIndexingUtilsTest.class);
  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private static EsBase row(Instant updatedAt) {
    return row(updatedAt, null);
  }

  private static EsBase row(Instant updatedAt, String baseId) {
    EsBase row = new EsBase();
    row.setBase_updated_at(updatedAt);
    row.setBase_id(baseId);
    return row;
  }

  private static Instant ts(long plusSeconds) {
    return T0.plusSeconds(plusSeconds);
  }

  @Nested
  @DisplayName("isPoisonError")
  class IsPoisonError {

    @Test
    @DisplayName("Deterministic mapping/parsing error types are poison")
    void given_deterministicErrorTypes_should_classifyAsPoison() {
      assertThat(EsIndexingUtils.isPoisonError("strict_dynamic_mapping_exception")).isTrue();
      assertThat(EsIndexingUtils.isPoisonError("mapper_parsing_exception")).isTrue();
      assertThat(EsIndexingUtils.isPoisonError("document_parsing_exception")).isTrue();
      assertThat(EsIndexingUtils.isPoisonError("illegal_argument_exception")).isTrue();
    }

    @Test
    @DisplayName("Transient error types and null are not poison")
    void given_transientErrorTypesOrNull_should_notClassifyAsPoison() {
      assertThat(EsIndexingUtils.isPoisonError("es_rejected_execution_exception")).isFalse();
      assertThat(EsIndexingUtils.isPoisonError("circuit_breaking_exception")).isFalse();
      assertThat(EsIndexingUtils.isPoisonError("cluster_block_exception")).isFalse();
      assertThat(EsIndexingUtils.isPoisonError(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("computeNewCursor")
  class ComputeNewCursor {

    @Test
    @DisplayName("Partial batch advances the cursor to the boundary timestamp")
    void given_partialBatch_should_advanceToBoundary() {
      List<EsBase> batch = List.of(row(ts(1)), row(ts(2)), row(ts(3)));

      Instant cursor = EsIndexingUtils.computeNewCursor(batch, 10, "model", LOG);

      assertThat(cursor).isEqualTo(ts(3));
    }

    @Test
    @DisplayName("Full batch steps back to the last complete timestamp group")
    void given_fullBatchWithDistinctTimestamps_should_advanceToLastCompleteGroup() {
      // The boundary group may have been cut by the LIMIT: rows sharing ts(3) could exist beyond
      // the batch, so the cursor must stop at ts(2) and re-fetch the ts(3) group next round.
      List<EsBase> batch = List.of(row(ts(1)), row(ts(2)), row(ts(3)));

      Instant cursor = EsIndexingUtils.computeNewCursor(batch, 3, "model", LOG);

      assertThat(cursor).isEqualTo(ts(2));
    }

    @Test
    @DisplayName("Full batch cut inside a shared boundary group does not skip the group")
    void given_fullBatchEndingOnSharedTimestamp_should_notAdvancePastSharedGroup() {
      // Rows 3 and 4 share ts(2) and the LIMIT cut the group: advancing to ts(2) would skip the
      // remaining ts(2) rows forever (queries fetch with strictly-greater-than).
      List<EsBase> batch = List.of(row(ts(1)), row(ts(1)), row(ts(2)), row(ts(2)));

      Instant cursor = EsIndexingUtils.computeNewCursor(batch, 4, "model", LOG);

      assertThat(cursor).isEqualTo(ts(1));
    }

    @Test
    @DisplayName("Degenerate full batch sharing one timestamp advances past it to keep progress")
    void given_fullBatchWithSingleSharedTimestamp_should_advancePastItToAvoidInfiniteLoop() {
      List<EsBase> batch = List.of(row(ts(5)), row(ts(5)), row(ts(5)));

      Instant cursor = EsIndexingUtils.computeNewCursor(batch, 3, "model", LOG);

      assertThat(cursor).isEqualTo(ts(5));
    }

    @Test
    @DisplayName("Null boundary timestamp yields a null cursor (caller keeps the old cursor)")
    void given_nullBoundaryTimestamp_should_returnNull() {
      List<EsBase> batch = List.of(row(ts(1)), row(null));

      Instant cursor = EsIndexingUtils.computeNewCursor(batch, 10, "model", LOG);

      assertThat(cursor).isNull();
    }

    @Test
    @DisplayName("Single-row partial batch advances to that row's timestamp")
    void given_singleRowPartialBatch_should_advanceToItsTimestamp() {
      List<EsBase> batch = List.of(row(ts(7)));

      Instant cursor = EsIndexingUtils.computeNewCursor(batch, 10, "model", LOG);

      assertThat(cursor).isEqualTo(ts(7));
    }
  }

  @Nested
  @DisplayName("capCursorToGraceWindow")
  class CapCursorToGraceWindow {

    @Test
    @DisplayName("Cursor inside the grace window is capped to now minus the window")
    void given_cursorInsideGraceWindow_should_capToNowMinusWindow() {
      // A row updated 10s ago must not move the cursor past now-60s: a transaction that flushed
      // its timestamps earlier but has not committed yet could still surface behind it.
      Instant now = ts(1000);

      Instant capped = EsIndexingUtils.capCursorToGraceWindow(ts(990), now, 60);

      assertThat(capped).isEqualTo(ts(940));
    }

    @Test
    @DisplayName("Cursor older than the grace window is kept as-is")
    void given_cursorOlderThanGraceWindow_should_keepCursor() {
      Instant now = ts(1000);

      Instant capped = EsIndexingUtils.capCursorToGraceWindow(ts(900), now, 60);

      assertThat(capped).isEqualTo(ts(900));
    }

    @Test
    @DisplayName("Cursor exactly at the window boundary is kept as-is")
    void given_cursorExactlyAtBoundary_should_keepCursor() {
      Instant now = ts(1000);

      Instant capped = EsIndexingUtils.capCursorToGraceWindow(ts(940), now, 60);

      assertThat(capped).isEqualTo(ts(940));
    }

    @Test
    @DisplayName("Zero grace window disables the cap for timestamps up to now")
    void given_zeroGraceWindow_should_notCapPastTimestamps() {
      Instant now = ts(1000);

      Instant capped = EsIndexingUtils.capCursorToGraceWindow(ts(999), now, 0);

      assertThat(capped).isEqualTo(ts(999));
    }

    @Test
    @DisplayName("Negative grace window is clamped to zero and never caps into the future")
    void given_negativeGraceWindow_should_clampToZero() {
      // A misconfigured negative window must not push the cap past now, which would silently
      // re-enable the commit-visibility race.
      Instant now = ts(1000);

      assertThat(EsIndexingUtils.capCursorToGraceWindow(ts(999), now, -30)).isEqualTo(ts(999));
      assertThat(EsIndexingUtils.capCursorToGraceWindow(ts(1030), now, -30)).isEqualTo(now);
    }
  }

  @Nested
  @DisplayName("Keyset cursor")
  class KeysetCursor {

    @Test
    @DisplayName("Keyset batch returns the last row's timestamp and id")
    void given_keysetBatch_should_returnLastRowTimestampAndId() {
      // A batch with distinct timestamps: the cursor must be the last row's pair, not the greatest
      // timestamp of the batch alone.
      List<EsBase> batch = List.of(row(ts(1), "a"), row(ts(2), "b"), row(ts(3), "c"));

      IndexingCursor cursor = EsIndexingUtils.computeKeysetCursor(batch);

      assertThat(cursor).isEqualTo(new IndexingCursor(ts(3), "c"));
    }

    @Test
    @DisplayName("Full batch sharing one timestamp advances on the id alone")
    void given_fullKeysetBatchSharingOneTimestamp_should_advanceOnTheIdAlone() {
      // Unlike computeNewCursor, a keyset handler can safely advance past a full batch where every
      // row shares the same timestamp: the id makes progress observable even when the timestamp
      // does not move.
      List<EsBase> batch = List.of(row(ts(5), "a"), row(ts(5), "b"), row(ts(5), "c"));

      IndexingCursor cursor = EsIndexingUtils.computeKeysetCursor(batch);

      assertThat(cursor).isEqualTo(new IndexingCursor(ts(5), "c"));
    }

    @Test
    @DisplayName("Null timestamp on the last row yields a null cursor")
    void given_keysetBatchWithNullTimestamp_should_returnNull() {
      List<EsBase> batch = List.of(row(ts(1), "a"), row(null, "b"));

      IndexingCursor cursor = EsIndexingUtils.computeKeysetCursor(batch);

      assertThat(cursor).isNull();
    }

    @Test
    @DisplayName("Grace-window cap moving the timestamp drops the last id")
    void given_capMovesTheTimestamp_should_dropTheLastId() {
      Instant now = ts(1000);
      IndexingCursor cursor = new IndexingCursor(ts(990), "a");

      IndexingCursor capped = EsIndexingUtils.capToGraceWindow(cursor, now, 60);

      assertThat(capped).isEqualTo(new IndexingCursor(ts(940), null));
    }

    @Test
    @DisplayName("Grace-window cap not moving the timestamp keeps the last id")
    void given_capDoesNotMoveTheTimestamp_should_keepTheLastId() {
      Instant now = ts(1000);
      IndexingCursor cursor = new IndexingCursor(ts(900), "a");

      IndexingCursor capped = EsIndexingUtils.capToGraceWindow(cursor, now, 60);

      assertThat(capped).isEqualTo(cursor);
    }

    @Test
    @DisplayName("Negative grace window never caps into the future")
    void given_negativeGraceWindow_should_notCapIntoTheFuture() {
      Instant now = ts(1000);
      IndexingCursor cursor = new IndexingCursor(ts(1030), "a");

      IndexingCursor capped = EsIndexingUtils.capToGraceWindow(cursor, now, -30);

      assertThat(capped).isEqualTo(new IndexingCursor(now, null));
    }
  }
}
