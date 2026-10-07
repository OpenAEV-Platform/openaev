package io.openaev.api.ioc_validation.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.IocValidationStatus;
import io.openaev.database.model.IocValidationTestKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** An IOC validation row of the list: identity, status and outcome counters, no JSON documents. */
public record IocValidationSimpleOutput(
    @JsonProperty("ioc_validation_id") @NotBlank String id,
    @JsonProperty("ioc_validation_external_id") @NotBlank String externalId,
    @JsonProperty("ioc_validation_name") @NotBlank String name,
    @JsonProperty("ioc_validation_requested_by") String requestedBy,
    @JsonProperty("ioc_validation_status") @NotNull IocValidationStatus status,
    @JsonProperty("ioc_validation_requested_test_kinds") @NotNull
        List<IocValidationTestKind> requestedTestKinds,
    @JsonProperty("ioc_validation_iocs_count") @Schema(requiredMode = REQUIRED) int iocsCount,
    @JsonProperty("ioc_validation_pairs_count") @Schema(requiredMode = REQUIRED) int pairsCount,
    @JsonProperty("ioc_validation_prevented_count") @Schema(requiredMode = REQUIRED)
        int preventedCount,
    @JsonProperty("ioc_validation_detected_count") @Schema(requiredMode = REQUIRED)
        int detectedCount,
    @JsonProperty("ioc_validation_missed_count") @Schema(requiredMode = REQUIRED) int missedCount,
    @JsonProperty("ioc_validation_error_count") @Schema(requiredMode = REQUIRED) int errorCount,
    @JsonProperty("ioc_validation_scenario_id") String scenarioId,
    @JsonProperty("ioc_validation_simulation_id") String simulationId,
    @JsonProperty("ioc_validation_created_at") @NotNull Instant createdAt,
    @JsonProperty("ioc_validation_updated_at") @NotNull Instant updatedAt) {}
