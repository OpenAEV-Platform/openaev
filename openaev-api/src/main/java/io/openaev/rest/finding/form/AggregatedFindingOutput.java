package io.openaev.rest.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.asset.endpoint.form.EndpointSimple;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import io.openaev.rest.injector.output.InjectorSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@JsonInclude(NON_NULL)
public class AggregatedFindingOutput {

  @Schema(description = "Finding Id")
  @JsonProperty("finding_id")
  @NotBlank
  private String id;

  @Schema(
      description = "Represents the data type being extracted.",
      example = "text, number, port, portscan, ipv4, ipv6, credentials, cve")
  @JsonProperty("finding_type")
  @NotNull
  private ContractOutputType type;

  @Schema(description = "Finding Value")
  @JsonProperty("finding_value")
  @NotBlank
  private String value;

  @Schema(description = "First time the finding was seen")
  @JsonProperty("finding_created_at")
  @NotNull
  private Instant creationDate;

  @Schema(description = "Last time the finding was seen")
  @JsonProperty("finding_updated_at")
  @NotNull
  private Instant updateDate;

  @Schema(description = "Assets linked to the finding (any asset type, not only endpoints)")
  @JsonProperty("finding_assets")
  @NotNull
  private Set<EndpointSimple> assets;

  @Schema(description = "Asset groups linked to assets")
  @JsonProperty("finding_asset_groups")
  private Set<AssetGroupSimple> assetGroups;

  @Schema(
      description =
          "Injector that produced this finding (null if the finding was created manually, e.g."
              + " via the API, without a real inject/injector behind it)")
  @JsonProperty("finding_source")
  private InjectorSimple source;
}
