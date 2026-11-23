package io.openaev.api.asset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AssetOwnerRole;
import io.openaev.database.model.AssetRelationSource;
import jakarta.validation.constraints.NotNull;

/**
 * An owner to add to an asset (#9725): exactly one of a person ({@code asset_owner_user_id}) and a
 * team ({@code asset_owner_team_id}). The source defaults to manual; a sync or a collector writing
 * through the API names itself.
 */
public record AssetOwnerInput(
    @JsonProperty("asset_owner_user_id") String userId,
    @JsonProperty("asset_owner_team_id") String teamId,
    @JsonProperty("asset_owner_role") @NotNull AssetOwnerRole role,
    @JsonProperty("asset_owner_source") AssetRelationSource source) {}
