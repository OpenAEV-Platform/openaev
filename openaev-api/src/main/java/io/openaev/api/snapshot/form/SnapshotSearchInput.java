package io.openaev.api.snapshot.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.time.Instant;

/**
 * Request body of both bulk snapshot search endpoints. {@code since} and {@code cursor} are
 * mutually exclusive (validated in the service, see FR26).
 *
 * <p>{@code pageSize} and {@code safetyLagSeconds} deliberately carry no Bean Validation bound: the
 * service clamps them instead, so a mis-tuned unattended poller degrades to a valid page rather
 * than failing its whole cycle on a 400. The lower bound of the lag is not expressible as an
 * annotation anyway — it depends on the engine grace window.
 */
public record SnapshotSearchInput(
    @Nullable
        @Schema(description = "Opaque resume cursor from a previous page")
        @JsonProperty("cursor")
        String cursor,
    @Nullable
        @Schema(description = "Full reconciliation lower bound; mutually exclusive with cursor")
        @JsonProperty("since")
        Instant since,
    @Nullable
        @Schema(description = "Page size, default 500, capped at 1000")
        @JsonProperty("page_size")
        Integer pageSize,
    @Nullable
        @Schema(
            description = "Safety lag in seconds, default 120, clamped to [max(60, grace), 3600]")
        @JsonProperty("safety_lag_seconds")
        Integer safetyLagSeconds) {}
