package io.openaev.rest.inject.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.model.InjectDependency;
import io.openaev.database.model.InjectorContract;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiIdListSerializer;
import io.openaev.helper.MultiIdSetSerializer;
import io.openaev.helper.MultiModelSerializer;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.ovh.OvhSmsContract;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
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
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(description = "Simulation ID of the inject", implementation = String.class)
  private Exercise exercise;

  @JsonProperty("inject_scenario")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(description = "Scenario ID of the inject", implementation = String.class)
  private Scenario scenario;

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
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @ArraySchema(schema = @Schema(description = "Tags of the inject", implementation = String.class))
  private Set<Tag> tags;

  @JsonProperty("inject_type")
  @Schema(description = "Type of the inject")
  public String injectType;

  @JsonProperty("inject_teams")
  @JsonSerialize(using = MultiIdListSerializer.class)
  @ArraySchema(schema = @Schema(description = "Teams of the inject", implementation = String.class))
  private List<Team> teams;

  @JsonProperty("inject_assets")
  @JsonSerialize(using = MultiIdListSerializer.class)
  @ArraySchema(
      schema = @Schema(description = "Assets of the inject", implementation = String.class))
  private List<Asset> assets;

  @JsonProperty("inject_asset_groups")
  @JsonSerialize(using = MultiIdListSerializer.class)
  @ArraySchema(
      schema = @Schema(description = "Asset groups of the inject", implementation = String.class))
  private List<AssetGroup> assetGroups;

  @JsonProperty("inject_content")
  @Schema(description = "Content of the inject")
  private ObjectNode content;

  @JsonProperty("inject_documents")
  @JsonSerialize(using = MultiModelSerializer.class)
  @ArraySchema(
      schema = @Schema(implementation = String.class, description = "Document of the inject"))
  private List<InjectDocument> documents = new ArrayList<>();

  @JsonProperty("inject_communications")
  @JsonSerialize(using = MultiModelSerializer.class)
  @ArraySchema(
      schema = @Schema(implementation = String.class, description = "Communication of the inject"))
  private List<Communication> communications = new ArrayList<>();

  @JsonProperty("inject_expectations")
  @JsonSerialize(using = MultiModelSerializer.class)
  @ArraySchema(
      schema = @Schema(implementation = String.class, description = "Expectation of the inject"))
  private List<InjectExpectation> expectations = new ArrayList<>();

  @JsonProperty("listened")
  @Schema(description = "Stream listener value of the inject")
  private boolean isListened;

  @JsonProperty("header")
  @Schema(description = "Header of the inject")
  private String header;

  @JsonProperty("footer")
  @Schema(description = "Footer of the inject")
  private String footer;

  @JsonProperty("inject_users_number")
  @Schema(description = "Count of users targeted by the inject")
  public Long numberOfTargetUsers;

  @JsonProperty("inject_date")
  @Schema(description = "Date of the inject")
  private Instant date;

  @JsonProperty("inject_communications_number")
  @Schema(description = "Communications count of the inject")
  public Long communicationsNumber;

  @JsonProperty("inject_communications_not_ack_number")
  @Schema(description = "Communications not ack count of the inject")
  private Long communicationsNotAckNumber;

  @JsonProperty("inject_sent_at")
  @Schema(description = "Sent date of the inject")
  public Instant sentAt;

  @JsonProperty("inject_kill_chain_phases")
  @ArraySchema(schema = @Schema(description = "Kill chain phase of the inject"))
  public List<KillChainPhase> killChainPhases;

  @JsonProperty("inject_attack_patterns")
  @ArraySchema(schema = @Schema(description = "Attack pattern of the inject"))
  public List<AttackPattern> attackPatterns;

  @JsonProperty("inject_testable")
  @Schema(description = "Testable state of the inject")
  public boolean canBeTested() {
    return EmailContract.TYPE.equals(this.getInjectType())
        || OvhSmsContract.TYPE.equals(this.getInjectType());
  }

  @JsonProperty("inject_healthchecks")
  @ArraySchema(schema = @Schema(description = "Healthcheck of the inject"))
  private List<HealthCheck> healthchecks = new ArrayList<>();

  @JsonProperty("inject_contract_domains")
  @Schema(description = "Domain of the inject")
  public Set<Domain> getDomains() {
    return injectorContract != null ? injectorContract.getDomains() : new HashSet<>();
  }

  @JsonProperty("inject_ready")
  @Schema(description = "Ready state of the inject")
  public boolean isReady() {
    return healthchecks.isEmpty()
        || healthchecks.stream()
            .noneMatch(
                healthcheck ->
                    HealthCheck.Detail.MANDATORY_CONTENT.equals(healthcheck.getDetail()));
  }
}
