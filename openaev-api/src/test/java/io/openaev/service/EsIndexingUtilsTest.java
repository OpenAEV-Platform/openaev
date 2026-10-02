package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.IndexingStatus;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.model.EsBase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
    EsBase row = new EsBase();
    row.setBase_updated_at(updatedAt);
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
  @DisplayName("isReindexRequested")
  class IsReindexRequested {

    private static Optional<IndexingStatus> statusAt(Instant cursor) {
      IndexingStatus status = new IndexingStatus();
      status.setType("expectation-inject");
      status.setLastIndexing(cursor);
      return Optional.of(status);
    }

    @Test
    @DisplayName("A missing status row requests a reset (never initialized or row deleted)")
    void given_missingRow_should_requestReset() {
      assertThat(EsIndexingUtils.isReindexRequested(Optional.empty())).isTrue();
    }

    @Test
    @DisplayName("The far-future sentinel cursor requests a reset")
    void given_sentinelCursor_should_requestReset() {
      assertThat(
              EsIndexingUtils.isReindexRequested(
                  statusAt(EsIndexingUtils.REINDEX_REQUESTED_CURSOR)))
          .isTrue();
      // Anything at or beyond the sentinel is a request too: a clock cannot legitimately be there.
      assertThat(
              EsIndexingUtils.isReindexRequested(
                  statusAt(EsIndexingUtils.REINDEX_REQUESTED_CURSOR.plusSeconds(1))))
          .isTrue();
    }

    @Test
    @DisplayName("A sentinel shifted by a round trip (time zone, dropped time part) still requests")
    void given_shiftedSentinelCursor_should_stillRequestReset() {
      // The widest UTC offset is 14 hours: a sentinel read back through a local-time conversion
      // must not turn into a regular cursor and lose the reset silently.
      assertThat(
              EsIndexingUtils.isReindexRequested(
                  statusAt(EsIndexingUtils.REINDEX_REQUESTED_CURSOR.minus(Duration.ofHours(14)))))
          .isTrue();
      assertThat(
              EsIndexingUtils.isReindexRequested(
                  statusAt(EsIndexingUtils.REINDEX_REQUESTED_CURSOR.minus(Duration.ofDays(1)))))
          .isTrue();
      // The request range starts at the threshold, inclusive.
      assertThat(
              EsIndexingUtils.isReindexRequested(
                  statusAt(EsIndexingUtils.REINDEX_REQUESTED_THRESHOLD)))
          .isTrue();
      assertThat(
              EsIndexingUtils.isReindexRequested(
                  statusAt(EsIndexingUtils.REINDEX_REQUESTED_THRESHOLD.minusSeconds(1))))
          .isFalse();
    }

    @Test
    @DisplayName("A regular cursor, epoch and wall-clock included, does not request a reset")
    void given_regularCursor_should_notRequestReset() {
      assertThat(EsIndexingUtils.isReindexRequested(statusAt(T0))).isFalse();
      assertThat(EsIndexingUtils.isReindexRequested(statusAt(Instant.EPOCH))).isFalse();
      assertThat(EsIndexingUtils.isReindexRequested(statusAt(Instant.now()))).isFalse();
    }

    @Test
    @DisplayName("The threshold leaves a wide margin under the sentinel and above any real clock")
    void given_threshold_should_sitFarBelowSentinelAndFarAboveNow() {
      assertThat(EsIndexingUtils.REINDEX_REQUESTED_THRESHOLD)
          .isBefore(EsIndexingUtils.REINDEX_REQUESTED_CURSOR.minus(Duration.ofDays(365)))
          .isAfter(Instant.now().plus(Duration.ofDays(365L * 1000)));
    }
  }

  @Nested
  @DisplayName("requestReindexAtStartup - in-process reset request")
  class StartupResetRequest {

    private final String model = "reset-request-test-" + UUID.randomUUID();

    private static Optional<IndexingStatus> statusAt(String type, Instant cursor) {
      IndexingStatus status = new IndexingStatus();
      status.setType(type);
      status.setLastIndexing(cursor);
      return Optional.of(status);
    }

    @AfterEach
    void clearRequest() {
      EsIndexingUtils.reindexRequestFulfilled(model);
    }

    @Test
    @DisplayName("An in-process request wins over a regular row until the reset is fulfilled")
    void given_startupRequest_should_requestResetUntilFulfilled() {
      assertThat(EsIndexingUtils.isReindexRequested(model, statusAt(model, T0))).isFalse();

      EsIndexingUtils.requestReindexAtStartup(model);

      // The row may show a regular cursor (an older pod overwrote the sentinel mid-round): the
      // in-process request still triggers the reset at this startup.
      assertThat(EsIndexingUtils.isReindexRequested(model, statusAt(model, T0))).isTrue();
      assertThat(EsIndexingUtils.isReindexRequested(model, statusAt(model, Instant.EPOCH)))
          .isTrue();
      assertThat(EsIndexingUtils.isReindexRequested(model, Optional.empty())).isTrue();
      // Other models are unaffected.
      assertThat(EsIndexingUtils.isReindexRequested("other-" + model, statusAt(model, T0)))
          .isFalse();

      EsIndexingUtils.reindexRequestFulfilled(model);

      assertThat(EsIndexingUtils.isReindexRequested(model, statusAt(model, T0))).isFalse();
    }

    @Test
    @DisplayName("Fulfilling clears only the in-process half: the row-level request still holds")
    void given_fulfilledRequest_should_keepHonouringTheRow() {
      EsIndexingUtils.requestReindexAtStartup(model);
      EsIndexingUtils.reindexRequestFulfilled(model);

      assertThat(EsIndexingUtils.isReindexRequested(model, Optional.empty())).isTrue();
      assertThat(
              EsIndexingUtils.isReindexRequested(
                  model, statusAt(model, EsIndexingUtils.REINDEX_REQUESTED_CURSOR)))
          .isTrue();
    }
  }

  @Nested
  @DisplayName("isRollingDeployReset")
  class IsRollingDeployReset {

    private final String model = "rolling-reset-test-" + UUID.randomUUID();

    private static Optional<IndexingStatus> statusAt(String type, Instant cursor) {
      IndexingStatus status = new IndexingStatus();
      status.setType(type);
      status.setLastIndexing(cursor);
      return Optional.of(status);
    }

    @AfterEach
    void clearRequest() {
      EsIndexingUtils.reindexRequestFulfilled(model);
    }

    @Test
    @DisplayName(
        "A missing row (fresh install, single-instance reset) is not a rolling-deploy reset")
    void given_missingRow_should_notBeRollingDeployReset() {
      assertThat(EsIndexingUtils.isRollingDeployReset(model, Optional.empty())).isFalse();
    }

    @Test
    @DisplayName("The sentinel on the row and the in-process request both are")
    void given_sentinelOrStartupRequest_should_beRollingDeployReset() {
      assertThat(
              EsIndexingUtils.isRollingDeployReset(
                  model, statusAt(model, EsIndexingUtils.REINDEX_REQUESTED_CURSOR)))
          .isTrue();
      assertThat(EsIndexingUtils.isRollingDeployReset(model, statusAt(model, T0))).isFalse();

      EsIndexingUtils.requestReindexAtStartup(model);

      assertThat(EsIndexingUtils.isRollingDeployReset(model, statusAt(model, T0))).isTrue();
      assertThat(EsIndexingUtils.isRollingDeployReset(model, Optional.empty())).isTrue();
    }
  }

  @Nested
  @DisplayName("persistCursor - compare-and-set on the cursor the round read")
  class PersistCursor {

    private final IndexingStatusRepository repository = mock(IndexingStatusRepository.class);

    @Test
    @DisplayName("A round that read a row writes through the compare-and-set on that value")
    void given_readCursor_should_advanceFromIt() {
      when(repository.advanceCursorFrom(
              "asset", T0, ts(60), EsIndexingUtils.REINDEX_REQUESTED_THRESHOLD))
          .thenReturn(1);

      boolean persisted =
          EsIndexingUtils.persistCursor(
              repository, new EsIndexingUtils.CursorAdvance("asset", T0, ts(60)), LOG);

      assertThat(persisted).isTrue();
      verify(repository)
          .advanceCursorFrom("asset", T0, ts(60), EsIndexingUtils.REINDEX_REQUESTED_THRESHOLD);
      verify(repository, never()).insertCursorIfAbsent(any(), any());
    }

    @Test
    @DisplayName("A round that found no row only ever inserts, never overwrites")
    void given_noReadCursor_should_insertIfAbsent() {
      when(repository.insertCursorIfAbsent("asset", ts(60))).thenReturn(1);

      boolean persisted =
          EsIndexingUtils.persistCursor(
              repository, new EsIndexingUtils.CursorAdvance("asset", null, ts(60)), LOG);

      assertThat(persisted).isTrue();
      verify(repository).insertCursorIfAbsent("asset", ts(60));
      verify(repository, never()).advanceCursorFrom(any(), any(), any(), any());
    }

    @Test
    @DisplayName("A refused write (the row changed since it was read) is reported, not retried")
    void given_rowChangedSinceRead_should_reportRefusedWrite() {
      when(repository.advanceCursorFrom(any(), any(), any(), any())).thenReturn(0);
      when(repository.insertCursorIfAbsent(any(), any())).thenReturn(0);

      assertThat(
              EsIndexingUtils.persistCursor(
                  repository, new EsIndexingUtils.CursorAdvance("asset", T0, ts(60)), LOG))
          .isFalse();
      assertThat(
              EsIndexingUtils.persistCursor(
                  repository, new EsIndexingUtils.CursorAdvance("asset", null, ts(60)), LOG))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("indexResetFailedMessage")
  class IndexResetFailedMessage {

    @Test
    @DisplayName("The message names the model, the index, the failure and the recovery steps")
    void given_failure_should_nameIndexAndRecovery() {
      String message =
          EsIndexingUtils.indexResetFailedMessage(
              "expectation-inject", "openaev_expectation-inject", "its index could not be deleted");

      assertThat(message)
          .contains("'expectation-inject'")
          .contains("'openaev_expectation-inject'")
          .contains("its index could not be deleted")
          .contains("retried at the next startup")
          .contains("UPDATE indexing_status SET indexing_status_indexing_date = to_timestamp(0)")
          .contains("WHERE indexing_status_type = 'expectation-inject'");
    }
  }
}
