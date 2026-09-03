package io.openaev.api.snapshot.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;

/** Response envelope of both bulk snapshot search endpoints, per FR29. */
public record SnapshotSearchOutput<T>(
    @Schema(description = "Page of observations") @JsonProperty("observations")
        List<T> observations,
    @Nullable
        @Schema(
            description =
                "Opaque resume cursor; an empty page echoes back the cursor it was given, so it is always safe to store")
        @JsonProperty("next_cursor")
        String nextCursor,
    @Schema(description = "Whether another page is expected to follow") @JsonProperty("has_more")
        boolean hasMore,
    @Schema(description = "Inclusive upper bound of this page's window")
        @JsonProperty("snapshot_window_end")
        Instant snapshotWindowEnd,
    @Schema(
            description =
                "Approximate indexing horizon of this stream; a readiness signal, not the exact indexing cursor")
        @JsonProperty("indexed_through")
        Instant indexedThrough,
    @Schema(description = "Server time at which this response was computed")
        @JsonProperty("server_time")
        Instant serverTime,
    @Schema(description = "Always \"eventual\"") @JsonProperty("consistency_mode")
        String consistencyMode,
    @Schema(description = "Whether the window is current with the indexing horizon")
        @JsonProperty("snapshot_ready")
        boolean snapshotReady) {}
