package io.openaev.utilstest;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.exception.InvalidDateRangeException;
import io.openaev.utils.OpenSearchUtils;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.query_dsl.Query;

@DisplayName("OpenSearch Utils tests")
class OpenSearchUtilsTest {

  @Test
  @DisplayName("buildDateRangeQuery should accept a valid date range")
  void buildDateRangeQuery_whenStartBeforeEnd_thenReturnsQuery() {
    // -- PREPARE --
    Instant start = Instant.parse("2025-01-01T00:00:00Z");
    Instant end = Instant.parse("2025-01-31T00:00:00Z");

    // -- EXECUTE & ASSERT --
    assertDoesNotThrow(() -> OpenSearchUtils.buildDateRangeQuery("created_at", start, end));
  }

  @Test
  @DisplayName("buildDateRangeQuery should throw InvalidDateRangeException when start is after end")
  void buildDateRangeQuery_whenStartAfterEnd_thenThrowsInvalidDateRangeException() {
    // -- PREPARE --
    Instant start = Instant.parse("2025-01-31T00:00:00Z");
    Instant end = Instant.parse("2025-01-01T00:00:00Z");

    // -- EXECUTE & ASSERT --
    InvalidDateRangeException exception =
        assertThrows(
            InvalidDateRangeException.class,
            () -> OpenSearchUtils.buildDateRangeQuery("created_at", start, end));
    assertEquals("Start date must be before end date", exception.getMessage());
  }

  @Test
  @DisplayName("buildDateRangeQuery should throw InvalidDateRangeException when start equals end")
  void buildDateRangeQuery_whenStartEqualsEnd_thenThrowsInvalidDateRangeException() {
    // -- PREPARE --
    Instant date = Instant.parse("2025-01-15T00:00:00Z");

    // -- EXECUTE & ASSERT --
    assertThrows(
        InvalidDateRangeException.class,
        () -> OpenSearchUtils.buildDateRangeQuery("created_at", date, date));
  }

  @Test
  @DisplayName("buildKeysetPredicate should build a should(gt, and(eq, gt)) disjunction")
  void buildKeysetPredicate_shouldBuildExpectedShape() {
    // -- PREPARE --
    Instant ts = Instant.parse("2025-01-15T10:00:00.500Z");

    // -- EXECUTE --
    Query query =
        OpenSearchUtils.buildKeysetPredicate("base_updated_at", "base_id.keyword", ts, "abc123");

    // -- ASSERT --
    assertTrue(query.isBool());
    List<Query> should = query.bool().should();
    assertEquals(2, should.size());
    assertEquals("1", query.bool().minimumShouldMatch());

    // Branch 1: strictly after ts
    Query strictlyAfter = should.get(0);
    assertTrue(strictlyAfter.isRange());
    assertEquals("base_updated_at", strictlyAfter.range().field());
    assertEquals(ts.toString(), strictlyAfter.range().gt().toString());
    assertNull(strictlyAfter.range().gte());

    // Branch 2: equal to ts and strictly after id
    Query sameTsAfterId = should.get(1);
    assertTrue(sameTsAfterId.isBool());
    List<Query> must = sameTsAfterId.bool().must();
    assertEquals(2, must.size());

    assertEquals("base_updated_at", must.get(0).range().field());
    assertEquals(ts.toString(), must.get(0).range().gte().toString());
    assertEquals(ts.toString(), must.get(0).range().lte().toString());

    assertEquals("base_id.keyword", must.get(1).range().field());
    assertEquals("abc123", must.get(1).range().gt().toString());
  }
}
