package io.openaev.api.finding;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import java.time.Instant;
import lombok.Builder;

@Builder
public record StableFindingSummaryOutput(
    @JsonProperty("finding_id") String id,
    @JsonProperty("finding_type") ContractOutputType type,
    @JsonProperty("finding_value") String value,
    @JsonProperty("finding_first_seen") Instant firstSeen,
    @JsonProperty("finding_last_seen") Instant lastSeen,
    @JsonProperty("finding_occurrences") long occurrences,
    @JsonProperty("finding_locations_count") long locationsCount,
    @JsonProperty("finding_assets_count") long assetsCount,
    @JsonProperty("finding_teams_count") long teamsCount,
    @JsonProperty("finding_users_count") long usersCount,
    @JsonProperty("finding_asset_groups_count") long assetGroupsCount) {}
