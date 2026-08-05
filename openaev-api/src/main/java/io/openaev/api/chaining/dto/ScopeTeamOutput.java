package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A team that is in scope (allowlisted and not denylisted) for a workflow.")
public record ScopeTeamOutput(
    @Schema(description = "ID of the team") @JsonProperty("team_id") String id,
    @Schema(description = "Name of the team") @JsonProperty("team_name") String name) {}
