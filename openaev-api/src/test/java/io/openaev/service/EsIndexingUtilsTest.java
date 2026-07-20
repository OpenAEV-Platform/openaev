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
}
