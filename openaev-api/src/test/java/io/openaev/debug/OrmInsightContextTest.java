package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OrmInsightContext")
class OrmInsightContextTest {

  @AfterEach
  void cleanup() {
    OrmInsightContext.clear();
  }

  @Test
  @DisplayName("record is a no-op when no request is active")
  void noOpWhenInactive() {
    // Must not throw and must not create anything.
    OrmInsightContext.record("select 1", 5);
    assertThat(OrmInsightContext.current()).isNull();
  }

  @Test
  @DisplayName("record feeds the active request stats")
  void feedsActiveStats() {
    OrmInsightContext.start("GET /x");

    OrmInsightContext.record("select 1", 3);
    OrmInsightContext.record("select 1", 4);

    RequestQueryStats stats = OrmInsightContext.current();
    assertThat(stats).isNotNull();
    assertThat(stats.totalQueries()).isEqualTo(2);
    assertThat(stats.requestDescription()).isEqualTo("GET /x");
  }

  @Test
  @DisplayName("clear removes the active stats")
  void clearRemoves() {
    OrmInsightContext.start("GET /x");
    OrmInsightContext.clear();
    assertThat(OrmInsightContext.current()).isNull();
  }
}
