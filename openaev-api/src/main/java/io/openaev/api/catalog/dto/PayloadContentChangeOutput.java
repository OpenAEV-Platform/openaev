package io.openaev.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.openaev.api.threat_arsenal.dto.PayloadVersionChangeOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** One change of a payload; an update or a save also says what its version changed. */
public record PayloadContentChangeOutput(
    @JsonUnwrapped ContentChangeOutput change,
    @Schema(description = "The version it came from; empty when the change made no version")
        @JsonProperty("content_change_from_version")
        String fromVersion,
    @Schema(description = "The fields its version changed; empty when it made no version")
        @JsonProperty("content_change_fields")
        List<PayloadVersionChangeOutput> fields) {}
