package io.openbas.rest.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openbas.database.model.ContractOutputType;
import io.openbas.rest.asset.endpoint.form.EndpointSimple;
import io.openbas.rest.asset_group.form.AssetGroupSimple;
import io.openbas.rest.exercise.form.ExerciseSimple;
import io.openbas.rest.inject.output.InjectSimple;
import io.openbas.rest.scenario.form.ScenarioSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(NON_NULL)
public class FindingOutput {

  @Schema(description = "Finding Id")
  @JsonProperty("finding_id")
  @NotBlank
  private String id;

  @Schema(description = "Finding field that corresponds to the key of the output parser")
  @JsonProperty("finding_field")
  @NotBlank
  private String field;

  @Schema(
      description = "Represents the data type being extracted.",
      example = "text, number, port, portscan, ipv4, ipv6, credentials")
  @JsonProperty("finding_type")
  @NotNull
  private ContractOutputType type;

  @Schema(description = "Finding Value")
  @JsonProperty("finding_value")
  @NotBlank
  private String value;

  @Schema(description = "Finding Name")
  @JsonProperty("finding_name")
  @NotBlank
  private String name;

  @JsonProperty("finding_created_at")
  @NotNull
  private Instant creationDate;

  @Schema(description = "Tags that correspond to the output parser tags")
  @JsonProperty("finding_tags")
  private Set<String> tagIds;

  @Schema(description = "Inject linked to finding")
  @JsonProperty("finding_inject")
  @NotBlank
  private InjectSimple inject;

  @Schema(description = "Simulation linked to inject")
  @JsonProperty("finding_simulation")
  private ExerciseSimple simulation;

  @Schema(description = "Scenario linked to inject")
  @JsonProperty("finding_scenario")
  private ScenarioSimple scenario;

  @Schema(description = "Endpoint linked to finding")
  @JsonProperty("finding_assets")
  @NotNull
  private Set<EndpointSimple> endpoints;

  @Schema(description = "Asset groups linked to endpoints")
  @JsonProperty("finding_asset_groups")
  private Set<AssetGroupSimple> assetGroups;
}
