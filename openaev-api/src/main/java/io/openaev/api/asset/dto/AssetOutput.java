package io.openaev.api.asset.dto;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.AssetSubCategory;
import io.openaev.database.model.Endpoint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

/**
 * Generic, category-agnostic view of an {@link Asset}. Unlike {@code EndpointOutput} this covers
 * every asset type (endpoints, AI targets, identities, cloud / web / network / generic assets), so
 * an asset group holding any mix of asset types can be listed uniformly. Endpoint-only and AI-only
 * fields are populated when relevant and omitted otherwise.
 */
@Setter
@Getter
@Builder
@JsonInclude(NON_NULL)
public class AssetOutput {

  @Schema(description = "Asset Id")
  @JsonProperty("asset_id")
  @NotBlank
  private String id;

  @Schema(description = "Asset name")
  @JsonProperty("asset_name")
  @NotBlank
  private String name;

  @Schema(description = "Asset type discriminator (Asset / Endpoint / SecurityPlatform)")
  @JsonProperty("asset_type")
  private String type;

  @Schema(description = "Asset category")
  @JsonProperty("asset_category")
  private AssetCategory category;

  @Schema(description = "Asset subcategory")
  @JsonProperty("asset_subcategory")
  private AssetSubCategory subcategory;

  @Schema(description = "Asset criticality")
  @JsonProperty("asset_criticality")
  private AssetCriticality criticality;

  @Schema(description = "Tags")
  @JsonProperty("asset_tags")
  private Set<String> tags;

  @Schema(description = "Hostname (network-reachable assets)")
  @JsonProperty("asset_hostname")
  private String hostname;

  @Schema(description = "Platform (endpoints only)")
  @JsonProperty("endpoint_platform")
  private Endpoint.PLATFORM_TYPE platform;

  @Schema(description = "AI target provider (AI targets only)")
  @JsonProperty("ai_target_provider")
  private Asset.AI_TARGET_PROVIDER aiTargetProvider;

  @Schema(description = "Whether the asset belongs to the asset group statically or dynamically")
  @JsonProperty("is_static")
  private Boolean isStatic;

  public static AssetOutput from(Asset asset, boolean isStatic) {
    // Unproxy before the instanceof: a lazy proxy typed as Asset would hide the Endpoint subtype
    // and drop the platform from the output.
    Object unproxied = Hibernate.unproxy(asset);
    return AssetOutput.builder()
        .id(asset.getId())
        .name(asset.getName())
        .type(asset.getType())
        .category(asset.getCategory())
        .subcategory(asset.getSubcategory())
        .criticality(asset.getCriticality())
        .tags(asset.getTags().stream().map(tag -> tag.getId()).collect(Collectors.toSet()))
        .hostname(asset.getHostname())
        .platform(unproxied instanceof Endpoint endpoint ? endpoint.getPlatform() : null)
        .aiTargetProvider(asset.getAiTargetProvider())
        .isStatic(isStatic)
        .build();
  }
}
