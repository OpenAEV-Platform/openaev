package io.openaev.api.snapshot.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;

/** One attack observation document of the bulk snapshot export (FR4, signed off with OpenGRC). */
public record AttackObservationOutput(
    @Schema(description = "Observation id") @JsonProperty("id") String id,
    @Schema(description = "Last update timestamp") @JsonProperty("updated_at") Instant updatedAt,
    @Schema(description = "Asset id") @JsonProperty("asset_id") String assetId,
    @Schema(description = "Scenario id") @JsonProperty("scenario_id") String scenarioId,
    @Schema(description = "Id of the last simulation that produced this observation")
        @JsonProperty("last_simulation_id")
        String lastSimulationId,
    @Schema(description = "Security platforms that reported on this technique")
        @JsonProperty("platforms_reporting")
        Set<String> platformsReporting,
    @Schema(description = "Asset name") @JsonProperty("asset_name") String assetName,
    @Schema(description = "Endpoint hostname") @JsonProperty("endpoint_hostname")
        String endpointHostname,
    @Schema(description = "Endpoint platform") @JsonProperty("endpoint_platform")
        String endpointPlatform,
    @Schema(description = "Tenant name") @JsonProperty("tenant_name") String tenantName,
    @Schema(description = "Attack pattern external id") @JsonProperty("attack_pattern_external_id")
        String attackPatternExternalId,
    @Schema(description = "Attack pattern name") @JsonProperty("attack_pattern_name")
        String attackPatternName,
    @Schema(description = "Scenario name") @JsonProperty("scenario_name") String scenarioName,
    @Schema(description = "Name of the last simulation that produced this observation")
        @JsonProperty("last_simulation_name")
        String lastSimulationName,
    @Schema(description = "Expectation type") @JsonProperty("expectation_type")
        String expectationType,
    @Schema(description = "Expectation status") @JsonProperty("expectation_status")
        String expectationStatus,
    @Schema(description = "Total attempts") @JsonProperty("attempts_total") Long attemptsTotal,
    @Schema(description = "Successful attempts") @JsonProperty("attempts_success")
        Long attemptsSuccess,
    @Schema(description = "Coverage ratio") @JsonProperty("coverage_ratio") Double coverageRatio,
    @Schema(description = "Security platforms that succeeded") @JsonProperty("platforms_succeeded")
        Set<String> platformsSucceeded,
    @Schema(description = "Last verification timestamp") @JsonProperty("last_verified_at")
        Instant lastVerifiedAt) {}
