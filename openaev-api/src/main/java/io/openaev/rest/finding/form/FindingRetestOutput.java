package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * One queued re-test: the finding and the inject that re-tests it. The inject id is the run id to
 * follow; when it finishes, the finding status says whether it was reproduced.
 */
public record FindingRetestOutput(
    @Schema(description = "Finding re-tested") @JsonProperty("finding_id") @NotBlank
        String findingId,
    @Schema(description = "Inject queued to re-test the finding")
        @JsonProperty("retest_inject_id")
        @NotBlank
        String retestInjectId) {}
