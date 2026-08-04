package io.openaev.utilstest;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.exception.InvalidDateRangeException;
import io.openaev.utils.OpenSearchUtils;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
