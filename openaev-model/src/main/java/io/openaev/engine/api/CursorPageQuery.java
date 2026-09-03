package io.openaev.engine.api;

import jakarta.annotation.Nullable;
import java.time.Instant;

/**
 * A single page request for {@link io.openaev.engine.EngineService#searchCursorPaged}: reads one
 * model index in {@code (base_updated_at, base_id)} order, resuming from an arbitrary point in that
 * order, without an offset.
 *
 * <p>{@code since} and {@code after} are mutually exclusive on a well-behaved call (FR26); this
 * record does not enforce it, that validation belongs to the API layer calling this method.
 *
 * @param since inclusive lower bound; {@code null} on a full reconciliation
 * @param after exclusive resume point; {@code null} on the first page
 * @param windowEnd inclusive upper bound, never {@code null}
 * @param size the page size, must be in {@code [1, EngineService#CURSOR_PAGE_MAX_SIZE]}
 */
public record CursorPageQuery(
    @Nullable Instant since, @Nullable Keyset after, Instant windowEnd, int size) {

  /**
   * The exclusive resume point: strictly after {@code ts}, or equal to {@code ts} and after {@code
   * id}.
   */
  public record Keyset(Instant ts, String id) {}
}
