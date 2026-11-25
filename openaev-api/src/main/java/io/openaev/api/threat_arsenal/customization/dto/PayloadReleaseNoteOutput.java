package io.openaev.api.threat_arsenal.customization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PayloadReleaseNoteOutput(
    @JsonProperty("release_version") @NotBlank String version,
    @JsonProperty("release_changelog") @NotBlank String changelog) {}
