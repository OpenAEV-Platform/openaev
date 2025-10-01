package io.openaev.rest.asset.endpoint.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

  @Schema(
      description =
          "The endpoint is associated with an asset group, either statically or dynamically.")
  @JsonProperty("is_static")
  private Boolean isStatic;
}
