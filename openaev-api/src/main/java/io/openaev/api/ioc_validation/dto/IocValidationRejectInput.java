package io.openaev.api.ioc_validation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Rejection of a waiting IOC validation. */
public record IocValidationRejectInput(
    @JsonProperty("ioc_validation_reason")
        @Schema(description = "Optional reason reported back to OpenCTI (max 2000 chars)")
        @Size(max = IocValidationRejectInput.MAX_REASON_LENGTH)
        String reason) {

  public static final int MAX_REASON_LENGTH = 2000;
}
