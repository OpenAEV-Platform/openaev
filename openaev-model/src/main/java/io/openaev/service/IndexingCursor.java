package io.openaev.service;

import java.time.Instant;

/**
 * Cursor persisted after an indexing round.
 *
 * <p>{@code lastId} is null for timestamp-only handlers, and also whenever the grace-window cap has
 * moved the timestamp: the id then belongs to a row that is no longer at the cursor's instant, so
 * keeping it would resume past rows that were never indexed.
 */
public record IndexingCursor(Instant timestamp, String lastId) {}
