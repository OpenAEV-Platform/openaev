package io.openaev.api.ioc_validation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.IocValidationOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/** One (indicator, security platform) pair of an IOC validation and its outcome. */
public record IocValidationPairOutput(
    @JsonProperty("pair_indicator_ref") @Schema(description = "STIX id of the indicator") @NotBlank
        String indicatorRef,
    @JsonProperty("pair_platform_ref")
        @Schema(description = "STIX id of the OpenCTI security platform identity")
        @NotBlank
        String platformRef,
    @JsonProperty("pair_deployed_on_ref")
        @Schema(description = "STIX id of the deployed-on relationship")
        @NotBlank
        String deployedOnRef,
    @JsonProperty("pair_platform_name")
        @Schema(description = "Security platform name received from OpenCTI")
        String platformName,
    @JsonProperty("pair_security_platform_id")
        @Schema(
            description =
                "Id of the matched OpenAEV security platform asset (absent when not matched)")
        String securityPlatformId,
    @JsonProperty("pair_outcome") @Schema(description = "Validation outcome (absent while pending)")
        IocValidationOutcome outcome,
    @JsonProperty("pair_outcome_reason")
        @Schema(description = "Why the outcome was reached (mainly for ERROR)")
        String outcomeReason,
    @JsonProperty("pair_evaluated_at") Instant evaluatedAt) {}
