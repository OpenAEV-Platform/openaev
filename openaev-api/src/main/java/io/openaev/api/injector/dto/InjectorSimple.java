package io.openaev.api.injector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record InjectorSimple(
    @Schema(description = "Injector name", example = "Nuclei")
        @JsonProperty("injector_name")
        @NotBlank
        String name,
    @Schema(description = "Injector type", example = "openaev_nuclei")
        @JsonProperty("injector_type")
        @NotBlank
        String type) {}
