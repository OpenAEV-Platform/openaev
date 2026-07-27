package io.openaev.api.expectations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Dismissal (or restoration) of the expectation-drift warning of a scenario, a simulation or an
 * atomic testing. Dismissing acknowledges that the drifted expectations were customized on purpose:
 * the warning is downgraded to a discreet indicator instead of the full button. The flag is
 * persisted in database so it is shared between users, and reset on realignment.
 */
public record ExpectationsDriftDismissInput(
    @Schema(
            description = "True to dismiss the drift warning, false to restore it",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("dismissed")
        boolean dismissed) {}
