package io.openaev.rest.inject.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Domain;
import io.openaev.database.model.InjectDependency;
import io.openaev.database.model.InjectorContract;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.helper.InjectModelHelper;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.ovh.OvhSmsContract;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InjectOutput {

  @JsonProperty("inject_id")
  @NotBlank
  @Schema(description = "ID of the inject")
  private String id;

  @JsonProperty("inject_title")
  @NotBlank
  @Schema(description = "Title of the inject")
  private String title;

  @JsonProperty("inject_enabled")
  @Schema(description = "Enabled state of the inject")
  private boolean enabled;

  @JsonProperty("inject_exercise")
  @Schema(description = "Simulation ID of the inject")
  private String exercise;

  @JsonProperty("inject_scenario")
  @Schema(description = "Scenario ID of the inject")
  private String scenario;

  @JsonProperty("inject_depends_duration")
  @NotNull
  @Min(value = 0L, message = "The value must be positive")
  @Schema(description = "Depend duration of the inject")
  private Long dependsDuration;

  @JsonProperty("inject_depends_on")
  @ArraySchema(schema = @Schema(description = "Inject dependencies of the inject"))
  private List<InjectDependency> dependsOn;

  @JsonProperty("inject_injector_contract")
  @Schema(description = "Injector contract of the inject")
  private InjectorContract injectorContract;

  @JsonProperty("inject_tags")
  @ArraySchema(schema = @Schema(description = "Tags of the inject"))
  private Set<String> tags;

  @JsonProperty("inject_ready")
  @Schema(description = "Ready state of the inject")
  public boolean isReady;

  @JsonProperty("inject_type")
  @Schema(description = "Type of the inject")
  public String injectType;

  @JsonProperty("inject_teams")
  @ArraySchema(schema = @Schema(description = "Teams of the inject"))
  private List<String> teams;

  @JsonProperty("inject_assets")
  @ArraySchema(schema = @Schema(description = "Assets of the inject"))
  private List<String> assets;

  @JsonProperty("inject_asset_groups")
  @ArraySchema(schema = @Schema(description = "Asset groups of the inject"))
  private List<String> assetGroups;

  @JsonProperty("inject_content")
  @Schema(description = "Content of the inject")
  private ObjectNode content;

  @JsonProperty("inject_healthchecks")
  @ArraySchema(schema = @Schema(description = "Healthchecks of the inject"))
  private List<HealthCheck> healthchecks = new ArrayList<>();

  @JsonProperty("inject_testable")
  @Schema(description = "Testable state of the inject")
  public boolean canBeTested() {
    return EmailContract.TYPE.equals(this.getInjectType())
        || OvhSmsContract.TYPE.equals(this.getInjectType());
  }

  @JsonProperty("inject_contract_domains")
  @Schema(description = "Domain of the inject")
  public Set<Domain> getDomains() {
    return injectorContract != null ? injectorContract.getDomains() : new HashSet<>();
  }

  public InjectOutput(
      String id,
      String title,
      boolean enabled,
      ObjectNode content,
      boolean allTeams,
      String exerciseId,
      String scenarioId,
      Long dependsDuration,
      InjectorContract injectorContract,
      String[] tags,
      String[] teams,
      String[] assets,
      String[] assetGroups,
      String injectType,
      InjectDependency injectDependency) {
    this.id = id;
    this.title = title;
    this.enabled = enabled;
    this.exercise = exerciseId;
    this.scenario = scenarioId;
    this.dependsDuration = dependsDuration;
    this.injectorContract = injectorContract;
    this.tags = tags != null ? new HashSet<>(Arrays.asList(tags)) : new HashSet<>();

    this.teams = teams != null ? new ArrayList<>(Arrays.asList(teams)) : new ArrayList<>();
    this.assets = assets != null ? new ArrayList<>(Arrays.asList(assets)) : new ArrayList<>();
    this.assetGroups =
        assetGroups != null ? new ArrayList<>(Arrays.asList(assetGroups)) : new ArrayList<>();

    this.isReady =
        InjectModelHelper.isReady(
            injectorContract, content, allTeams, this.teams, this.assets, this.assetGroups);
    this.injectType = injectType;
    this.teams = teams != null ? new ArrayList<>(Arrays.asList(teams)) : new ArrayList<>();
    this.content = content;

    if (injectDependency != null) {
      this.dependsOn = List.of(injectDependency);
    }
  }
}
