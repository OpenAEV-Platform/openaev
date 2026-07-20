package io.openaev.rest.asset.endpoint.form;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.AssetSubCategory;
import io.openaev.database.model.CloudProvider;
import io.openaev.database.model.Endpoint;
import io.openaev.rest.asset.form.AssetInput;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Creation/update input for an {@link Endpoint}, the generic carrier for every target asset
 * category. {@code endpoint_platform} / {@code endpoint_arch} are intentionally optional: agent and
 * collector registrations always provide them, while category-driven forms (web app, cloud,
 * network, ...) may omit them - the service / entity defaults them to {@code Unknown}.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class EndpointInput extends AssetInput {

  @JsonProperty("endpoint_platform")
  @Schema(types = {"string", "null"})
  private Endpoint.PLATFORM_TYPE platform;

  @JsonProperty("endpoint_arch")
  @Schema(types = {"string", "null"})
  private Endpoint.PLATFORM_ARCH arch;

  // The @JsonAlias on the renamed network fields keep already-deployed agents working: the
  // installed agent fleet registers with the legacy endpoint_* wire names and only self-updates
  // after a successful registration, so the old names must stay accepted on input.

  @JsonProperty("asset_ips")
  @JsonAlias("endpoint_ips")
  private String[] ips;

  @JsonProperty("asset_hostname")
  @JsonAlias("endpoint_hostname")
  private String hostname;

  @JsonProperty("asset_url")
  @JsonAlias("endpoint_url")
  @Schema(types = {"string", "null"})
  private String url;

  @JsonProperty("endpoint_agent_version")
  private String agentVersion;

  @JsonProperty("asset_mac_addresses")
  @JsonAlias("endpoint_mac_addresses")
  private String[] macAddresses;

  @Schema(description = "True if the endpoint is in an End of Life state")
  @JsonProperty("endpoint_is_eol")
  // Fixes a bug due to a new version of jackson and lombok
  // cf: https://github.com/projectlombok/lombok/issues/3978
  @Getter(onMethod_ = @JsonProperty("endpoint_is_eol"))
  private boolean isEol;

  // -- CATEGORIZATION --

  @JsonProperty("asset_category")
  @Schema(types = {"string", "null"})
  private AssetCategory category;

  @JsonProperty("asset_subcategory")
  @Schema(types = {"string", "null"})
  private AssetSubCategory subcategory;

  @JsonProperty("asset_criticality")
  @Schema(types = {"string", "null"})
  private AssetCriticality criticality;

  @JsonProperty("asset_internet_facing")
  @Schema(types = {"boolean", "null"})
  private Boolean internetFacing;

  @JsonProperty("asset_cloud_provider")
  @Schema(types = {"string", "null"})
  private CloudProvider cloudProvider;

  @JsonProperty("asset_cloud_native_type")
  @Schema(types = {"string", "null"})
  private String cloudNativeType;

  @JsonProperty("asset_cloud_region")
  @Schema(types = {"string", "null"})
  private String cloudRegion;

  @JsonProperty("asset_linked_person")
  @Schema(types = {"string", "null"})
  private String linkedPerson;

  @JsonProperty("asset_metadata")
  private Map<String, Object> metadata = new HashMap<>();
}
