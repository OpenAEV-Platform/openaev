package io.openaev.api.asset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AssetLinkType;
import io.openaev.database.model.AssetRelationSource;
import java.time.Instant;

/**
 * One link between two assets, with both ends named so the asset page can list it without a second
 * read. The direction is the stored one: {@code source} is the asset the link was added from; the
 * origin of the link (a person, an agent) is {@code asset_link_origin} (#9725).
 */
public record AssetLinkOutput(
    @JsonProperty("asset_link_source_id") String sourceId,
    @JsonProperty("asset_link_source_name") String sourceName,
    @JsonProperty("asset_link_source_type") String sourceType,
    @JsonProperty("asset_link_target_id") String targetId,
    @JsonProperty("asset_link_target_name") String targetName,
    @JsonProperty("asset_link_target_type") String targetType,
    @JsonProperty("asset_link_type") AssetLinkType type,
    @JsonProperty("asset_link_created_at") Instant createdAt,
    @JsonProperty("asset_link_origin") AssetRelationSource origin,
    @JsonProperty("asset_link_last_seen_at") Instant lastSeenAt) {}
