package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/** A single observation of a {@link StableFinding}, including its point-in-time evidence. */
@Getter
@Setter
@Entity
@Table(name = "finding_occurrences")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class, AuditableListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class FindingOccurrence implements TenantBase, Auditable {

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "finding_occurrence_id", updatable = false, nullable = false)
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_occurrence_stable_finding_id", updatable = false, nullable = false)
  @NotNull
  private StableFinding stableFinding;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_occurrence_inject_id", updatable = false)
  private Inject inject;

  @Column(name = "finding_occurrence_scan_id", updatable = false)
  private String scanId;

  @Queryable(filterable = true, sortable = true)
  @JsonProperty("finding_updated_at")
  @Column(name = "finding_occurrence_observed_at", updatable = false, nullable = false)
  @NotNull
  private Instant observedAt;

  @Column(name = "finding_occurrence_outcome")
  private String outcome;

  @Column(name = "finding_occurrence_evidence_detail", columnDefinition = "text")
  private String evidenceDetail;

  @Column(name = "finding_occurrence_status_detail", columnDefinition = "text")
  private String statusDetail;

  @Column(name = "finding_occurrence_raw_payload", columnDefinition = "text")
  private String rawPayload;

  @Column(name = "finding_occurrence_observed_severity")
  private String observedSeverity;

  @Column(name = "finding_occurrence_observed_severity_id")
  private Integer observedSeverityId;

  @Column(name = "finding_occurrence_source_finding_uid")
  private String sourceFindingUid;

  @Column(name = "finding_occurrence_title", columnDefinition = "text")
  private String title;

  @Column(name = "finding_occurrence_description", columnDefinition = "text")
  private String description;

  @Column(name = "finding_occurrence_risk", columnDefinition = "text")
  private String risk;

  @Type(StringArrayType.class)
  @Column(name = "finding_occurrence_categories", columnDefinition = "text[]")
  private String[] categories;

  @Type(StringArrayType.class)
  @Column(name = "finding_occurrence_attack_patterns", columnDefinition = "text[]")
  private String[] attackPatterns;

  @Column(name = "finding_occurrence_remediation", columnDefinition = "text")
  private String remediation;

  @Column(name = "finding_occurrence_compliance", columnDefinition = "text")
  private String compliance;

  @Column(name = "finding_occurrence_resource", columnDefinition = "text")
  private String resource;

  @Column(name = "finding_occurrence_resource_snapshot", columnDefinition = "text")
  private String resourceSnapshot;

  @Column(name = "finding_occurrence_resource_provider")
  private String resourceProvider;

  @Column(name = "finding_occurrence_resource_account")
  private String resourceAccount;

  @Column(name = "finding_occurrence_resource_region")
  private String resourceRegion;

  @Column(name = "finding_occurrence_resource_name")
  private String resourceName;

  @Column(name = "finding_occurrence_resource_type")
  private String resourceTypeSnapshot;

  @Column(name = "finding_occurrence_resource_service")
  private String resourceService;

  @Column(name = "finding_occurrence_location", columnDefinition = "text")
  private String location;

  @Enumerated(EnumType.STRING)
  @Column(name = "finding_occurrence_location_type")
  private FindingLocationType locationType;

  @Column(name = "finding_occurrence_location_key", length = 1024)
  private String locationKey;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_occurrence_location_asset_id")
  private Asset locationAsset;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_occurrence_location_user_id")
  private User locationUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_occurrence_location_team_id")
  private Team locationTeam;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "finding_occurrences_assets",
      joinColumns = @JoinColumn(name = "finding_occurrence_id"),
      inverseJoinColumns = @JoinColumn(name = "asset_id"))
  @Fetch(FetchMode.SUBSELECT)
  private List<Asset> assets = new ArrayList<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "finding_occurrences_users",
      joinColumns = @JoinColumn(name = "finding_occurrence_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @Fetch(FetchMode.SUBSELECT)
  private List<User> users = new ArrayList<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "finding_occurrences_teams",
      joinColumns = @JoinColumn(name = "finding_occurrence_id"),
      inverseJoinColumns = @JoinColumn(name = "team_id"))
  @Fetch(FetchMode.SUBSELECT)
  private List<Team> teams = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "finding_occurrence_target_role", nullable = false)
  @NotNull
  private FindingTargetRole targetRole = FindingTargetRole.EXECUTOR;

  @Enumerated(EnumType.STRING)
  @Column(name = "finding_occurrence_evidence_scope", nullable = false)
  @NotNull
  private FindingEvidenceScope evidenceScope = FindingEvidenceScope.INDIVIDUAL;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_occurrence_migrated_from", updatable = false)
  private Finding migratedFrom;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  @NotNull
  private Tenant tenant;

  @Column(name = "finding_occurrence_created_at", updatable = false, nullable = false)
  @NotNull
  private Instant createdAt = now();

  @Column(name = "finding_occurrence_updated_at", nullable = false)
  @NotNull
  private Instant updatedAt = now();

  @Queryable(filterable = true, sortable = true, path = "stableFinding.type")
  @JsonProperty("finding_type")
  public ContractOutputType getFindingType() {
    return stableFinding.getType();
  }

  @Queryable(filterable = true, sortable = true, path = "stableFinding.value")
  @JsonProperty("finding_value")
  public String getFindingValue() {
    return stableFinding.getValue();
  }

  @Queryable(filterable = true, sortable = true, path = "observedAt")
  @JsonProperty("finding_created_at")
  public Instant getFindingCreatedAt() {
    return observedAt;
  }

  @Queryable(filterable = true, dynamicValues = true, path = "inject.id")
  @JsonProperty("finding_inject_id")
  public String getFindingInjectId() {
    return inject == null ? null : inject.getId();
  }

  @Queryable(filterable = true, dynamicValues = true, path = "inject.exercise.id")
  @JsonProperty("finding_simulation")
  public String getFindingSimulationId() {
    return inject == null || inject.getExercise() == null ? null : inject.getExercise().getId();
  }

  @Queryable(filterable = true, dynamicValues = true, path = "inject.exercise.scenario.id")
  @JsonProperty("finding_scenario")
  public String getFindingScenarioId() {
    return inject == null
            || inject.getExercise() == null
            || inject.getExercise().getScenario() == null
        ? null
        : inject.getExercise().getScenario().getId();
  }

  @Queryable(filterable = true, dynamicValues = true, path = "assets.id")
  @JsonProperty("finding_assets")
  public List<Asset> getFindingAssets() {
    return assets;
  }

  @Queryable(filterable = true, dynamicValues = true, path = "teams.id")
  @JsonProperty("finding_teams")
  public List<Team> getFindingTeams() {
    return teams;
  }

  @Queryable(filterable = true, dynamicValues = true, path = "users.id")
  @JsonProperty("finding_users")
  public List<User> getFindingUsers() {
    return users;
  }

  @Queryable(filterable = true, dynamicValues = true, path = "inject.assetGroups.id")
  @JsonProperty("finding_asset_groups")
  public List<AssetGroup> getFindingAssetGroups() {
    return inject == null ? List.of() : inject.getAssetGroups();
  }

  @Override
  public boolean isListened() {
    return false;
  }
}
