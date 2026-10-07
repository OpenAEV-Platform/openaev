package io.openaev.api.ioc_validation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.IocValidationStatus;
import io.openaev.database.model.IocValidationTestKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** An IOC validation with its IOCs, pairs and decision, for the detail page. */
public record IocValidationOutput(
    @JsonProperty("ioc_validation_id") @NotBlank String id,
    @JsonProperty("ioc_validation_external_id")
        @Schema(description = "OpenCTI Ioc-Validation-Request internal id")
        @NotBlank
        String externalId,
    @JsonProperty("ioc_validation_name") @NotBlank String name,
    @JsonProperty("ioc_validation_description") String description,
    @JsonProperty("ioc_validation_requested_by") String requestedBy,
    @JsonProperty("ioc_validation_status") @NotNull IocValidationStatus status,
    @JsonProperty("ioc_validation_status_message") String statusMessage,
    @JsonProperty("ioc_validation_requested_test_kinds") @NotNull
        List<IocValidationTestKind> requestedTestKinds,
    @JsonProperty("ioc_validation_allowed_test_kinds")
        @Schema(description = "Tenant allow-list snapshot applied when the scenario was built")
        @NotNull
        List<IocValidationTestKind> allowedTestKinds,
    @JsonProperty("ioc_validation_iocs") @NotNull List<IocValidationIocOutput> iocs,
    @JsonProperty("ioc_validation_pairs") @NotNull List<IocValidationPairOutput> pairs,
    @JsonProperty("ioc_validation_scenario_id")
        @Schema(description = "Validation scenario id (link: /admin/scenarios/{id})")
        String scenarioId,
    @JsonProperty("ioc_validation_simulation_id")
        @Schema(description = "Simulation id once approved (link: /admin/simulations/{id})")
        String simulationId,
    @JsonProperty("ioc_validation_opencti_url")
        @Schema(description = "Link to the request in OpenCTI")
        String openctiUrl,
    @JsonProperty("ioc_validation_decided_by")
        @Schema(description = "Id of the user who approved or rejected")
        String decidedById,
    @JsonProperty("ioc_validation_decided_by_name")
        @Schema(description = "Name of the user who approved or rejected")
        String decidedByName,
    @JsonProperty("ioc_validation_decided_at") Instant decidedAt,
    @JsonProperty("ioc_validation_completed_at") Instant completedAt,
    @JsonProperty("ioc_validation_created_at") @NotNull Instant createdAt,
    @JsonProperty("ioc_validation_updated_at") @NotNull Instant updatedAt) {}
