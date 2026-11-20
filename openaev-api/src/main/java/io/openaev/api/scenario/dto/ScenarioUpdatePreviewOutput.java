package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * What an update or a rollback changes, the payload versions it installs or runs revoked, and the
 * injects that lose a dependency.
 */
public record ScenarioUpdatePreviewOutput(
    @JsonProperty("scenario_update_changes") @NotNull List<ScenarioChangeOutput> changes,
    @JsonProperty("scenario_update_payloads_to_install") @NotNull
        List<ScenarioPayloadVersionOutput> payloadsToInstall,
    @JsonProperty("scenario_update_revoked_payloads") @NotNull
        List<ScenarioPayloadVersionOutput> revokedPayloads,
    @JsonProperty("scenario_update_lost_dependencies") @NotNull List<String> lostDependencies) {}
