package io.openaev.api.health_check.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Health check response.
 *
 * <p>When the endpoint is called without {@code details=true}, only the {@code status} field is
 * present. Size fields are omitted from JSON when {@code null}.
 */
@Schema(description = "Detailed health check response.")
@JsonInclude(JsonInclude.Include.NON_NULL)
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
