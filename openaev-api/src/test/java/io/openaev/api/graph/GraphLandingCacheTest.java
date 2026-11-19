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

@DisplayName("Graph landing cache: per tenant scope and marking clearance, time-bounded")
class GraphLandingCacheTest {

  private static final List<String> NONE = List.of();

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

    assertThat(cache.get("k", List.of("b", "a"), NONE, reads::incrementAndGet)).isEqualTo(1);
    assertThat(cache.get("k", List.of("a", "b"), NONE, reads::incrementAndGet)).isEqualTo(1);
    now.addAndGet(GraphLandingCache.TTL_MILLIS);

    assertThat(cache.get("k", List.of("a", "b"), NONE, reads::incrementAndGet)).isEqualTo(2);
  }

  @Test
  @DisplayName("another tenant scope or another kind never shares an entry")
  void keyedByKindAndScope() {
    GraphLandingCache cache = new GraphLandingCache(clock);

    assertThat(cache.get("k", List.of("a"), NONE, () -> "a")).isEqualTo("a");
    assertThat(cache.get("k", List.of("b"), NONE, () -> "b")).isEqualTo("b");
    assertThat(cache.get("other", List.of("a"), NONE, () -> "other")).isEqualTo("other");
    assertThat(cache.size()).isEqualTo(3);
  }

  @Test
  @DisplayName("rows read under one marking clearance are never served to another clearance")
  void keyedByClearance() {
    GraphLandingCache cache = new GraphLandingCache(clock);

    // A reader cleared for TLP:GREEN and TLP:RED fills the entry first.
    assertThat(cache.get("k", List.of("a"), List.of("tlp_red", "tlp_green"), () -> "red"))
        .isEqualTo("red");

    // Same tenant, within the TTL: a TLP:GREEN reader and a reader with no clearance read again.
    assertThat(cache.get("k", List.of("a"), List.of("tlp_green"), () -> "green"))
        .isEqualTo("green");
    assertThat(cache.get("k", List.of("a"), NONE, () -> "none")).isEqualTo("none");
    assertThat(cache.get("k", List.of("a"), null, () -> "null")).isEqualTo("none");
    // The same clearance in another order is the same entry.
    assertThat(cache.get("k", List.of("a"), List.of("tlp_green", "tlp_red"), () -> "other"))
        .isEqualTo("red");
    assertThat(cache.size()).isEqualTo(3);
  }

  @Test
  @DisplayName("clear drops every entry, and the cache never grows past its bound")
  void clearAndBound() {
    GraphLandingCache cache = new GraphLandingCache(clock);
    for (int i = 0; i < GraphLandingCache.MAX_ENTRIES + 10; i++) {
      String tenant = "t" + i;
      cache.get("k", List.of(tenant), NONE, () -> tenant);
    }
    assertThat(cache.size()).isLessThanOrEqualTo(GraphLandingCache.MAX_ENTRIES);

    cache.clear();

    assertThat(cache.size()).isZero();
    assertThat(new GraphLandingCache().get("k", List.of("a"), NONE, () -> "fresh"))
        .isEqualTo("fresh");
  }
}
