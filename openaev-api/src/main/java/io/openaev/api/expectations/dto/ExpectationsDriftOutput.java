package io.openaev.api.expectations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Alignment report between the predefined expectations currently exposed by injector contracts (the
 * security posture templates) and the expectations actually stored inside the injects of a
 * scenario, a simulation or an atomic testing.
 *
 * <p>A drifted inject is not an error: users may have customized its expectations on purpose. The
 * report only surfaces that the validation requirements of the underlying contracts evolved since
 * the inject inherited them, so the user can decide whether to realign.
 */
public record ExpectationsDriftOutput(
    @Schema(description = "True when at least one inject drifted from its contract expectations")
        @JsonProperty("drift_detected")
        boolean driftDetected,
    @Schema(description = "Number of injects whose expectations drifted from their contract")
        @JsonProperty("drifted_inject_count")
        int driftedInjectCount,
    @Schema(description = "Number of injects whose injector contract exposes expectations")
        @JsonProperty("total_inject_count")
        int totalInjectCount) {}
