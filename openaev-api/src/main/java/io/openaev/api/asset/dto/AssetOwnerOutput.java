package io.openaev.api.asset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AssetOwnerRole;
import io.openaev.database.model.AssetRelationSource;
import java.time.Instant;

/**
 * One owner of an asset (#9725), with the asset and the owner named so a page or a sync reads it
 * without a second call. {@code asset_owner_kind} is {@code USER} or {@code TEAM}; the id of the
 * other kind is null.
 */
public record AssetOwnerOutput(
    @JsonProperty("asset_owner_id") String id,
    @JsonProperty("asset_id") String assetId,
    @JsonProperty("asset_name") String assetName,
    @JsonProperty("asset_owner_kind") String kind,
    @JsonProperty("asset_owner_user_id") String userId,
    @JsonProperty("asset_owner_team_id") String teamId,
    @JsonProperty("asset_owner_name") String name,
    @JsonProperty("asset_owner_role") AssetOwnerRole role,
    @JsonProperty("asset_owner_source") AssetRelationSource source,
    @JsonProperty("asset_owner_created_at") Instant createdAt) {

  public static final String KIND_USER = "USER";
  public static final String KIND_TEAM = "TEAM";
}
