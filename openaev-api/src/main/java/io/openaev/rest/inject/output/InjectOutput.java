package io.openaev.rest.inject.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectDependency;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.helper.InjectModelHelper;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.ovh.OvhSmsContract;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Stream;
import lombok.Data;

@Data
public class InjectOutput {

  @JsonProperty("inject_id")
  @NotBlank
  private String id;

  @JsonProperty("inject_title")
  @NotBlank
  private String title;

  @JsonProperty("inject_enabled")
  private boolean enabled;

  @JsonProperty("inject_exercise")
  private String exercise;

  @JsonProperty("inject_scenario")
  private String scenario;

  @JsonProperty("inject_depends_duration")
  @NotNull
  @Min(value = 0L, message = "The value must be positive")
  private Long dependsDuration;

  @JsonProperty("inject_depends_on")
  private List<InjectDependency> dependsOn;

  @JsonProperty("inject_injector_contract")
  private InjectorContract injectorContract;

  @JsonProperty("inject_tags")
  private Set<String> tags;

  @JsonProperty("inject_ready")
  public boolean isReady;

  @JsonProperty("inject_type")
  public String injectType;

  @JsonProperty("inject_teams")
  private List<String> teams;

  @JsonProperty("inject_assets")
  private List<String> assets;

  @JsonProperty("inject_asset_groups")
  private List<String> assetGroups;

  @JsonProperty("inject_content")
  private ObjectNode content;

  @JsonProperty("inject_healthchecks")
  private List<HealthCheck> healthchecks = new ArrayList<>();

  @JsonProperty("inject_testable")
  public boolean canBeTested() {
    return EmailContract.TYPE.equals(this.getInjectType())
        || OvhSmsContract.TYPE.equals(this.getInjectType());
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

  public InjectOutput(Inject inject) {
    this.id = inject.getId();
    this.title = inject.getTitle();
    this.enabled = inject.isEnabled();
    this.exercise = Optional.ofNullable(inject.getExercise()).map(Exercise::getId).orElse(null);
    this.scenario = Optional.ofNullable(inject.getScenario()).map(Scenario::getId).orElse(null);
    this.dependsDuration = inject.getDependsDuration();
    this.injectorContract = inject.getInjectorContract().orElse(null);
    this.tags =
        inject.getTags() != null
            ? new HashSet<>(inject.getTags().stream().map(Tag::getId).toList())
            : new HashSet<>();
    this.teams =
        inject.getTeams() != null
            ? inject.getTeams().stream().map(Team::getId).toList()
            : new ArrayList<>();
    this.assets =
        inject.getAssets() != null
            ? inject.getAssets().stream().map(Asset::getId).toList()
            : new ArrayList<>();
    this.assetGroups =
        inject.getAssetGroups() != null
            ? new ArrayList<>(inject.getAssetGroups().stream().map(AssetGroup::getId).toList())
            : new ArrayList<>();
    this.content = inject.getContent();
    this.isReady =
        InjectModelHelper.isReady(
            injectorContract,
            content,
            inject.isAllTeams(),
            this.teams,
            this.assets,
            this.assetGroups);
    this.injectType =
        inject
            .getInjectorContract()
            .map(InjectorContract::getInjector)
            .map(Injector::getType)
            .orElse(null);
    this.dependsOn =
        Optional.ofNullable(inject.getDependsOn())
            .map(List::stream)
            .flatMap(Stream::findAny)
            .stream()
            .toList();
  }
}
