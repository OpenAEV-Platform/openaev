package io.openaev.rest.asset.endpoint.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.AssetSubCategory;
import io.openaev.database.model.CloudProvider;
import io.openaev.database.model.Endpoint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
@JsonInclude(NON_NULL)
public class EndpointOutput {

  @Schema(description = "Asset Id")
  @JsonProperty("asset_id")
  @NotBlank
  private String id;

  @Schema(description = "Asset name")
  @JsonProperty("asset_name")
  @NotBlank
  private String name;

  @Schema(description = "Asset type")
  @JsonProperty("asset_type")
  private String type;

  @Schema(description = "Asset external reference")
  @JsonProperty("asset_external_reference")
  private String externalReference;

  @Schema(description = "List of agents")
  @JsonProperty("asset_agents")
  @NotNull
  private Set<AgentOutput> agents;

  @Schema(description = "Platform")
  @JsonProperty("endpoint_platform")
  @NotBlank
  private Endpoint.PLATFORM_TYPE platform;

  @Schema(description = "Architecture")
  @JsonProperty("endpoint_arch")
  @NotBlank
  private Endpoint.PLATFORM_ARCH arch;

  @Schema(description = "Tags")
  @JsonProperty("asset_tags")
  private Set<String> tags;

  @Schema(description = "Asset category")
  @JsonProperty("asset_category")
  private AssetCategory category;

  @Schema(description = "Asset subcategory")
  @JsonProperty("asset_subcategory")
  private AssetSubCategory subcategory;

  @Schema(description = "Asset criticality")
  @JsonProperty("asset_criticality")
  private AssetCriticality criticality;

  @Schema(description = "Whether the asset is internet-facing")
  @JsonProperty("asset_internet_facing")
  private Boolean internetFacing;

  @Schema(description = "Cloud provider")
  @JsonProperty("asset_cloud_provider")
  private CloudProvider cloudProvider;

  @Schema(description = "Cloud native type")
  @JsonProperty("asset_cloud_native_type")
  private String cloudNativeType;

  @Schema(description = "Cloud region")
  @JsonProperty("asset_cloud_region")
  private String cloudRegion;

  @Schema(description = "Linked person (user id) for identity assets")
  @JsonProperty("asset_linked_person")
  private String linkedPerson;

  @Schema(
      description =
          "The endpoint is associated with an asset group, either statically or dynamically.")
  @JsonProperty("is_static")
  private Boolean isStatic;
}
