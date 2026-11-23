package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * How scenarios built from an OpenCTI security coverage are scheduled in this tenant. Off by
 * default: a coverage builds and schedules its scenario exactly as before.
 *
 * @param readOnlyFirst only the read-only injects are enabled, and the schedule is left unset while
 *     an inject that is not read-only is present; a user with the launch right starts it through
 *     the existing recurrence endpoint
 */
public record CoverageScenarioSettingsInput(
    @JsonProperty("coverage_scenario_read_only_first") @NotNull Boolean readOnlyFirst) {}
