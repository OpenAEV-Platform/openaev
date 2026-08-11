package io.openaev.rest.health_check.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Detailed health check response, returned when the endpoint is called with {@code details=true}.
 *
 * <p>Sizes are expressed in bytes and are best effort: a {@code null} value means the metric could
 * not be retrieved from the dependency. They are computed periodically (not on every call) since
 * the health check endpoint is polled very frequently.
 */
@Schema(description = "Detailed health check response.")
public record HealthCheckDetailsOutput(
    @Schema(description = "Status of the platform", example = "success") @JsonProperty("status")
        String status,
    @Schema(description = "Size used by the PostgreSQL database, in bytes", example = "104857600")
        @JsonProperty("pg_used_size")
        Long pgUsedSize,
    @Schema(
            description = "Size used by the analytics engine indexes (replicas excluded), in bytes",
            example = "524288000")
        @JsonProperty("es_used_size")
        Long esUsedSize,
    @Schema(description = "Size used by the object storage bucket, in bytes", example = "20971520")
        @JsonProperty("s3_used_size")
        Long s3UsedSize) {}
