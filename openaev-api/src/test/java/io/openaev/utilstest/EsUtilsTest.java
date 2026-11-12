package io.openaev.utilstest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.engine.EsUtils;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EsUtils tests")
class EsUtilsTest {

  @Test
  @DisplayName("toEngineDate should write the fraction of a whole-second instant")
  void toEngineDate_whenWholeSecond_thenWritesZeroMillis() {
    // Instant.toString writes 2026-11-11T02:40:01Z, which the engine rounds up to .999.
    Instant instant = Instant.parse("2026-11-11T02:40:01Z");

    assertEquals("2026-11-11T02:40:01.000Z", EsUtils.toEngineDate(instant));
  }

  @Test
  @DisplayName("toEngineDate should keep milliseconds as they are")
  void toEngineDate_whenMillis_thenKeepsThem() {
    Instant instant = Instant.parse("2026-11-11T02:40:01.001Z");

    assertEquals("2026-11-11T02:40:01.001Z", EsUtils.toEngineDate(instant));
  }

  @Test
  @DisplayName("toEngineDate should drop digits below the millisecond")
  void toEngineDate_whenNanos_thenTruncatesToMillis() {
    Instant instant = Instant.parse("2026-11-11T02:40:01.123456789Z");

    assertEquals("2026-11-11T02:40:01.123Z", EsUtils.toEngineDate(instant));
  }
}
