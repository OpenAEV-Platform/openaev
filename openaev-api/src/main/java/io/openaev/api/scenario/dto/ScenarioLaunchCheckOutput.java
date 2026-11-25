package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioRequirementOutput;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** What a scenario's launch meets, each with what the launch drawer offers to fix it. */
public record ScenarioLaunchCheckOutput(
    @JsonProperty("launch_check_injects") @NotNull List<InjectTargetsOutput> injects,
    @JsonProperty("launch_check_revoked_injects") @NotNull List<RevokedInjectOutput> revokedInjects,
    @JsonProperty("launch_check_missing_requirements") @NotNull
        List<CatalogScenarioRequirementOutput> missingRequirements) {}
