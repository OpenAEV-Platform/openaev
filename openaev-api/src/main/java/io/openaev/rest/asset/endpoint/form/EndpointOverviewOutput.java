package io.openaev.rest.asset.endpoint.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.AssetSubCategory;
import io.openaev.database.model.CloudProvider;
import io.openaev.database.model.Endpoint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
@JsonInclude(NON_NULL)
public class EndpointOverviewOutput {

  @Schema(description = "Asset Id")
  @JsonProperty("asset_id")
  @NotBlank
  private String id;

  @Schema(description = "Asset name")
  @JsonProperty("asset_name")
  @NotBlank
  private String name;

  @Schema(description = "Asset description")
  @JsonProperty("asset_description")
  private String description;

  @Schema(description = "Hostname")
  @JsonProperty("asset_hostname")
  private String hostname;

  @Schema(description = "URL")
  @JsonProperty("asset_url")
  private String url;

  @Schema(description = "Platform")
  @JsonProperty("endpoint_platform")
  private Endpoint.PLATFORM_TYPE platform;

  @Schema(description = "Architecture")
  @JsonProperty("endpoint_arch")
  private Endpoint.PLATFORM_ARCH arch;

  @Schema(description = "List IPs")
  @JsonProperty("asset_ips")
  private Set<String> ips;

  @Schema(description = "Seen IP")
  @JsonProperty("asset_seen_ip")
  private String seenIp;

  @Schema(description = "List of MAC addresses")
  @JsonProperty("asset_mac_addresses")
  private Set<String> macAddresses;

  @Schema(description = "List of primary agents")
  @JsonProperty("asset_agents")
  @NotNull
  private Set<AgentOutput> agents;

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

  // -- AI TARGET (category = AI_TARGET) --
  // Connection metadata is safe to expose; the credential (ai_target_token) is intentionally never
  // surfaced in the overview.

  @Schema(description = "AI target provider (AI targets only)")
  @JsonProperty("ai_target_provider")
  private Asset.AI_TARGET_PROVIDER aiTargetProvider;

  @Schema(description = "AI target modality (AI targets only)")
  @JsonProperty("ai_target_modality")
  private Asset.AI_TARGET_MODALITY aiTargetModality;

  @Schema(description = "AI target endpoint URL (AI targets only)")
  @JsonProperty("ai_target_endpoint")
  private String aiTargetEndpoint;

  @Schema(description = "AI target model (AI targets only)")
  @JsonProperty("ai_target_model")
  private String aiTargetModel;

  @Schema(description = "AI target system prompt (AI targets only)")
  @JsonProperty("ai_target_system_prompt")
  private String aiTargetSystemPrompt;

  @Schema(description = "Free-form category-specific attributes")
  @JsonProperty("asset_metadata")
  private Map<String, Object> metadata;

  @Schema(description = "True if the endpoint is in an End of Life state")
  @JsonProperty("endpoint_is_eol")
  // Fixes a bug due to a new version of jackson and lombok
  // cf: https://github.com/projectlombok/lombok/issues/3978
  @Getter(onMethod_ = @JsonProperty("endpoint_is_eol"))
  private boolean isEol;
}
