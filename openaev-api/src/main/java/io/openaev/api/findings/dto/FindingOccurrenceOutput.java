package io.openaev.api.findings.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.inject.output.InjectSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Builder;

@Builder
public record FindingOccurrenceOutput(
    @JsonProperty("finding_occurrence_id") @NotBlank String id,
    @JsonProperty("finding_occurrence_date") @NotNull Instant date,
    @JsonProperty("finding_occurrence_field") String field,
    @Schema(description = "Null once the inject is deleted or its simulation reset")
        @JsonProperty("finding_occurrence_inject")
        InjectSimple inject,
    @JsonProperty("finding_occurrence_simulation") FindingOccurrenceContext simulation,
    @JsonProperty("finding_occurrence_scenario") FindingOccurrenceContext scenario) {}
