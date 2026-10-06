package io.openaev.api.ioc_validation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Approval of a waiting IOC validation, bound to the approval preview the operator confirmed. */
public record IocValidationApproveInput(
    @JsonProperty("ioc_validation_preview_fingerprint")
        @Schema(
            description =
                "Fingerprint of the approval preview the operator confirmed: the approval runs only"
                    + " if it plans the same tests and security platforms")
        @NotBlank
        String previewFingerprint) {}
