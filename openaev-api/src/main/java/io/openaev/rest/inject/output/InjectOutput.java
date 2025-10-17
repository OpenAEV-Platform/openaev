package io.openaev.rest.inject.output;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.converter.ContentConverter;
import io.openaev.database.model.*;
import io.openaev.database.model.Domain;
import io.openaev.database.model.InjectDependency;
import io.openaev.database.model.InjectorContract;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.helper.*;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.ovh.OvhSmsContract;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Convert;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

  @JsonProperty("inject_description")
  @Schema(description = "Description of the inject")
  private String description;

  @JsonProperty("inject_country")
  @Schema(description = "Country of the inject")
  private String country;

  @JsonProperty("inject_city")
  @Schema(description = "City of the inject")
  private String city;

  @JsonProperty("inject_enabled")
  @Schema(description = "Enabled state of the inject")
  private boolean enabled;

  @JsonProperty("inject_trigger_now_date")
  @Schema(description = "Trigger date of the inject")
  private Instant triggerNowDate;

  @JsonProperty("inject_content")
  @Convert(converter = ContentConverter.class)
  @Schema(description = "Content of the inject")
  private ObjectNode content;

  @JsonProperty("inject_created_at")
  @NotNull
  @Schema(description = "Creation date of the inject")
  private Instant createdAt = now();

  @JsonProperty("inject_updated_at")
  @NotNull
  @Schema(description = "Update date of the inject")
  private Instant updatedAt = now();

  @JsonProperty("inject_all_teams")
  @Schema(description = "All teams value of the inject")
  private boolean allTeams;

  @JsonProperty("inject_exercise")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @Schema(type = "string", description = "Simulation ID of the inject")
  private Exercise exercise;

  @JsonProperty("inject_scenario")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @Schema(type = "string", description = "Scenario ID of the inject")
  private Scenario scenario;

  @JsonProperty("inject_depends_on")
  @ArraySchema(schema = @Schema(description = "Dependencies of the inject"))
  private List<InjectDependency> dependsOn = new ArrayList<>();

  @JsonProperty("inject_depends_duration")
  @NotNull
  @Min(value = 0L, message = "The value must be positive")
  @Schema(description = "Depend duration of the inject")
  private Long dependsDuration;

  @JsonProperty("inject_injector_contract")
  @Schema(description = "Injector contract of the inject")
  private InjectorContract injectorContract;

  @JsonProperty("inject_user")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @Schema(type = "string", description = "User of the inject")
  private User user;

  @JsonProperty("inject_status")
  @Schema(description = "Status of the inject")
  private InjectStatus status;

  @JsonProperty("inject_collect_status")
  @Enumerated(EnumType.STRING)
  @Schema(description = "Collect execution status of the inject")
  private CollectExecutionStatus collectExecutionStatus;

  @JsonProperty("inject_tags")
  @JsonSerialize(using = MultiIdSetDeserializer.class)
  @ArraySchema(schema = @Schema(type = "string", description = "Tags of the inject"))
  private Set<Tag> tags;

  @JsonProperty("inject_teams")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @ArraySchema(schema = @Schema(type = "string", description = "Teams of the inject"))
  private List<Team> teams;

  @JsonProperty("inject_assets")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @ArraySchema(schema = @Schema(type = "string", description = "Assets of the inject"))
  private List<Asset> assets;

  @JsonProperty("inject_asset_groups")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @ArraySchema(schema = @Schema(type = "string", description = "Asset groups of the inject"))
  private List<AssetGroup> assetGroups;

  @JsonProperty("inject_documents")
  @JsonSerialize(using = MultiModelDeserializer.class)
  @ArraySchema(schema = @Schema(type = "string", description = "Documents of the inject"))
  private List<InjectDocument> documents = new ArrayList<>();

  @JsonProperty("inject_communications")
  @JsonSerialize(using = MultiModelDeserializer.class)
  @ArraySchema(schema = @Schema(type = "string", description = "Communications of the inject"))
  private List<Communication> communications = new ArrayList<>();

  @JsonProperty("inject_expectations")
  @JsonSerialize(using = MultiModelDeserializer.class)
  @ArraySchema(schema = @Schema(type = "string", description = "Expectations of the inject"))
  private List<InjectExpectation> expectations = new ArrayList<>();

  @JsonProperty("inject_users_number")
  @Schema(description = "Number of users tageted by the inject")
  public Long numberOfTargetUsers;

  @JsonProperty("inject_date")
  @Schema(description = "Date of the inject")
  private Instant date;

  @JsonProperty("inject_communications_number")
  @Schema(description = "Communications size of the inject")
  public Long communicationsNumber;

  @JsonProperty("inject_communications_not_ack_number")
  @Schema(description = "Communications not ack size of the inject")
  private Long communicationsNotAckNumber;

  @JsonProperty("inject_sent_at")
  @Schema(description = "Sent date of the inject")
  public Instant sentAt;

  @JsonProperty("inject_kill_chain_phases")
  @ArraySchema(schema = @Schema(description = "Kill chain phases of the inject"))
  public List<KillChainPhase> killChainPhases;

  @JsonProperty("inject_attack_patterns")
  @ArraySchema(schema = @Schema(description = "Attack pattern of the inject"))
  public List<AttackPattern> attackPatterns;

  @JsonProperty("inject_type")
  @Schema(description = "Type of the inject")
  private String type;

  @JsonProperty("inject_testable")
  @Schema(description = "Testable state of the inject")
  public boolean canBeTested() {
    return EmailContract.TYPE.equals(this.getType()) || OvhSmsContract.TYPE.equals(this.getType());
  }

  @JsonProperty("inject_healthchecks")
  @ArraySchema(schema = @Schema(description = "Healthchecks of the inject"))
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
