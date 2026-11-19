package io.openaev.api.graph;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Short-lived cache of the graph landing aggregates (#9447): the exposure ranking, the estate
 * overview and the top findings are tenant-wide GROUP BY reads, so they are computed at most once
 * per {@link #TTL_MILLIS} per tenant scope instead of at every page load.
 *
 * <p>Only tenant-scoped raw rows are cached, keyed by the sorted tenant ids of the request scope
 * AND the caller's marking clearance ({@code app.current_markings}, ADR-009): the rows are read
 * through the marking inspector, so rows computed under one clearance are never served to a reader
 * holding another. The resource gate of the caller (simulations, people) is applied on every
 * request after the cache, so a cached entry never carries one user's visibility to another.
 */
@Component
public class GraphLandingCache {

  /** How long an aggregate is served before it is read again. */
  static final long TTL_MILLIS = 60_000L;

  /** Entries kept at most; past it the cache is cleared rather than grown. */
  static final int MAX_ENTRIES = 512;

  private record Entry(long at, Object value) {}

  private final Map<String, Entry> entries = new ConcurrentHashMap<>();
  private final Clock clock;

  public GraphLandingCache() {
    this(Clock.systemUTC());
  }

  GraphLandingCache(Clock clock) {
    this.clock = clock;
  }

  /**
   * The cached value of {@code kind} for the tenant scope and the marking clearance, computed by
   * {@code read} when stale.
   *
   * @param clearance the marking ids the reader is cleared for, in any order; {@code null} or empty
   *     for no clearance
   */
  @SuppressWarnings("unchecked")
  public <T> T get(
      String kind, Collection<String> tenantIds, Collection<String> clearance, Supplier<T> read) {
    String key =
        kind
            + "|"
            + String.join(",", new TreeSet<>(tenantIds))
            + "|"
            + String.join(",", new TreeSet<>(clearance == null ? List.of() : clearance));
    long now = clock.millis();
    Entry hit = entries.get(key);
    if (hit != null && now - hit.at() < TTL_MILLIS) {
      return (T) hit.value();
    }
    T value = read.get();
    if (entries.size() >= MAX_ENTRIES) {
      entries.clear();
    }
    entries.put(key, new Entry(now, value));
    return value;
  }

  /** Drops every entry: the next read recomputes. */
  public void clear() {
    entries.clear();
  }

  int size() {
    return entries.size();
  }
}
