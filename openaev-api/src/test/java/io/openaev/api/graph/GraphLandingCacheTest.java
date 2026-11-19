package io.openaev.api.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Graph landing cache: per tenant scope, time-bounded")
class GraphLandingCacheTest {

  private final AtomicLong now = new AtomicLong(1_000_000L);

  private final Clock clock =
      new Clock() {
        @Override
        public ZoneOffset getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return Instant.ofEpochMilli(now.get());
        }
      };

  @Test
  @DisplayName("a value is read once per scope until its TTL expires")
  void servesUntilExpiry() {
    GraphLandingCache cache = new GraphLandingCache(clock);
    AtomicInteger reads = new AtomicInteger();

    assertThat(cache.get("k", List.of("b", "a"), reads::incrementAndGet)).isEqualTo(1);
    assertThat(cache.get("k", List.of("a", "b"), reads::incrementAndGet)).isEqualTo(1);
    now.addAndGet(GraphLandingCache.TTL_MILLIS);

    assertThat(cache.get("k", List.of("a", "b"), reads::incrementAndGet)).isEqualTo(2);
  }

  @Test
  @DisplayName("another tenant scope or another kind never shares an entry")
  void keyedByKindAndScope() {
    GraphLandingCache cache = new GraphLandingCache(clock);

    assertThat(cache.get("k", List.of("a"), () -> "a")).isEqualTo("a");
    assertThat(cache.get("k", List.of("b"), () -> "b")).isEqualTo("b");
    assertThat(cache.get("other", List.of("a"), () -> "other")).isEqualTo("other");
    assertThat(cache.size()).isEqualTo(3);
  }

  @Test
  @DisplayName("clear drops every entry, and the cache never grows past its bound")
  void clearAndBound() {
    GraphLandingCache cache = new GraphLandingCache(clock);
    for (int i = 0; i < GraphLandingCache.MAX_ENTRIES + 10; i++) {
      String tenant = "t" + i;
      cache.get("k", List.of(tenant), () -> tenant);
    }
    assertThat(cache.size()).isLessThanOrEqualTo(GraphLandingCache.MAX_ENTRIES);

    cache.clear();

    assertThat(cache.size()).isZero();
    assertThat(new GraphLandingCache().get("k", List.of("a"), () -> "fresh")).isEqualTo("fresh");
  }
}
