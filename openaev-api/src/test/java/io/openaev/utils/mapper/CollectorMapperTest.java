package io.openaev.utils.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.openaev.database.model.Collector;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Collector mapper: last execution")
class CollectorMapperTest {

  private static final Instant REGISTERED_AT = Instant.parse("2026-11-01T10:00:00Z");
  private static final Instant LAST_RUN_AT = Instant.parse("2026-11-25T18:00:00Z");

  private static Collector collector(boolean external, Instant updatedAt, Instant lastExecution) {
    Collector collector = new Collector();
    collector.setId("collector-id");
    collector.setExternal(external);
    collector.setUpdatedAt(updatedAt);
    collector.setLastExecution(lastExecution);
    return collector;
  }

  @Test
  @DisplayName("given a built-in collector that ran, should report its last execution")
  void given_builtinCollectorThatRan_should_reportLastExecution() {
    assertEquals(
        LAST_RUN_AT, CollectorMapper.lastExecution(collector(false, REGISTERED_AT, LAST_RUN_AT)));
  }

  @Test
  @DisplayName("given a built-in collector that never ran, should fall back to updatedAt")
  void given_builtinCollectorNeverRan_should_fallBackToUpdatedAt() {
    assertEquals(
        REGISTERED_AT, CollectorMapper.lastExecution(collector(false, REGISTERED_AT, null)));
  }

  @Test
  @DisplayName("given an external collector, should report updatedAt, stamped by its pings")
  void given_externalCollector_should_reportUpdatedAt() {
    assertEquals(
        LAST_RUN_AT, CollectorMapper.lastExecution(collector(true, LAST_RUN_AT, REGISTERED_AT)));
  }

  @Test
  @DisplayName("given a collector with no timestamps, should report no last execution")
  void given_collectorWithoutTimestamps_should_reportNull() {
    assertNull(CollectorMapper.lastExecution(collector(false, null, null)));
  }
}
