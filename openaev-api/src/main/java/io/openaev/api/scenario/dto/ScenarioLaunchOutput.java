package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** The simulation a launch started. */
public record ScenarioLaunchOutput(@JsonProperty("simulation_id") @NotBlank String simulationId) {}
