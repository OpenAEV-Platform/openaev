package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * How scenarios built from an OpenCTI security coverage run in this tenant. Both are off by
 * default: nothing waits out of the box.
 *
 * @param safeMode only read-only injects run; any other inject waits for an approval
 * @param approvalRequired outside safe mode, the scenario waits for an approval before its first
 *     run
 */
public record CoverageScenarioSettingsInput(
    @JsonProperty("coverage_scenario_safe_mode") @NotNull Boolean safeMode,
    @JsonProperty("coverage_scenario_approval_required") @NotNull Boolean approvalRequired) {}
