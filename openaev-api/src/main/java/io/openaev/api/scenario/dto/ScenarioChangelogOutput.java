package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** The changelog of a catalogue version an update brings. */
public record ScenarioChangelogOutput(
    @JsonProperty("scenario_changelog_version") @NotBlank String version,
    @JsonProperty("scenario_changelog") @NotBlank String changelog) {}
