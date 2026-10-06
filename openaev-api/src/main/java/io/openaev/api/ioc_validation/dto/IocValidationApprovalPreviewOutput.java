package io.openaev.api.ioc_validation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * What the approval of a waiting IOC validation would start now, planned by the approval itself
 * with the current IOC validation settings, DNS answers and security platforms: every IOC with the
 * test that would run or why it would not, and the pairs of the IOCs whose test runs, matched to
 * the OpenAEV security platforms.
 */
public record IocValidationApprovalPreviewOutput(
    @JsonProperty("ioc_validation_preview_fingerprint")
        @Schema(
            description =
                "Fingerprint of this preview, sent with the approval: it runs only if it plans the"
                    + " same")
        @NotBlank
        String fingerprint,
    @JsonProperty("ioc_validation_preview_iocs") @NotNull List<IocValidationIocOutput> iocs,
    @JsonProperty("ioc_validation_preview_pairs") @NotNull List<IocValidationPairOutput> pairs,
    @JsonProperty("ioc_validation_preview_blocker")
        @Schema(description = "Why the approval would start nothing now (absent when it can run)")
        String blocker) {}
