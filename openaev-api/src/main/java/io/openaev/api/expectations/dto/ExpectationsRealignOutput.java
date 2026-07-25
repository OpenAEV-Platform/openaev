package io.openaev.api.expectations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Result of a bulk realignment of inject expectations onto their injector contract templates. */
public record ExpectationsRealignOutput(
    @Schema(description = "Number of injects whose expectations were realigned onto their contract")
        @JsonProperty("realigned_inject_count")
        int realignedInjectCount) {}
