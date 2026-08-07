package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiIdListSerializer;
import io.openaev.helper.MultiIdSetSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.hibernate.annotations.*;

@Data
@Entity
@Table(name = "findings")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Finding implements TenantBase {

  @Id
  @Column(name = "finding_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("finding_id")
  @NotBlank
  private String id;

  @Queryable(searchable = true, filterable = true, sortable = true)
  @Column(name = "finding_field", nullable = false)
  @JsonProperty("finding_field")
  @NotBlank
  private String field;

  @Queryable(filterable = true, sortable = true, label = "finding type")
  @Column(name = "finding_type", updatable = false, nullable = false)
  @Enumerated(EnumType.STRING)
  @JsonProperty("finding_type")
  @NotNull
  protected ContractOutputType type;

  @Queryable(searchable = true, filterable = true, sortable = true)
  @Column(name = "finding_value", nullable = false)
  @JsonProperty("finding_value")
  @NotBlank
  protected String value;

  @Deprecated
  @Type(StringArrayType.class)
  @Column(name = "finding_labels", columnDefinition = "text[]")
  @JsonProperty("finding_labels")
  private String[] labels;

  @Queryable(searchable = true, filterable = true, sortable = true)
  @Column(name = "finding_name")
  @JsonProperty("finding_name")
  protected String name;

  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "findings_tags",
      joinColumns = @JoinColumn(name = "finding_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("finding_tags")
  @Queryable(filterable = true, dynamicValues = true, path = "tags.id")
  private Set<Tag> tags = new HashSet<>();

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  // The tenant here must be set automatically with the inject tenant when the finding is created by
  // the inject
  private Tenant tenant;

  // -- RELATION --

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_inject_id")
  @JsonProperty("finding_inject_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(implementation = String.class)
  @Queryable(filterable = true, dynamicValues = true, sortable = true, path = "inject.id")
  private Inject inject;

  // Read-only navigation side of the 1:1 relation owned by FindingTriage#finding. Exists solely so
  // the generic @Queryable filter engine (FilterUtilsJpa) can join through it to filter by
  // finding_triage_status; the actual status shown to clients is resolved via
  // FindingTriageService/FindingMapper, since a Finding with no FindingTriage row is virtually
  // UNTRIAGED rather than having a real DB value (see FindingTriageService#getCurrentStatus
  // javadoc). FindingDistinctSearchService special-cases the UNTRIAGED filter value to also match
  // findings with no row at all - a plain equality on this path would silently miss them.
  @JsonIgnore
  @OneToOne(mappedBy = "finding", fetch = FetchType.LAZY)
  @JsonProperty("finding_triage_status")
  @Queryable(
      filterable = true,
      path = "triage.status",
      refEnumClazz = FindingTriageStatus.class,
      label = "triage status")
  // Excluded to break the Finding <-> FindingTriage toString/equals/hashCode recursion (Lombok
  // @Data on both sides): mirrors the convention used for the mappedBy/inverse-navigation side of
  // a bidirectional relation elsewhere in this codebase (see SecurityPlatform#collectors /
  // #injectors), leaving the owning side (FindingTriage#finding) unexcluded.
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private FindingTriage triage;

  // -- AUDIT --

  @Queryable(filterable = true, sortable = true, label = "created at")
  @CreationTimestamp
  @Column(name = "finding_created_at", updatable = false, nullable = false)
  @JsonProperty("finding_created_at")
  @NotNull
  private Instant creationDate = now();

  @Queryable(filterable = true, sortable = true, label = "updated at")
  @UpdateTimestamp
  @Column(name = "finding_updated_at", nullable = false)
  @JsonProperty("finding_updated_at")
  @NotNull
  private Instant updateDate = now();

  // Relation
  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "findings_assets",
      joinColumns = @JoinColumn(name = "finding_id"),
      inverseJoinColumns = @JoinColumn(name = "asset_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("finding_assets")
  @Queryable(filterable = true, dynamicValues = true, path = "assets.id")
  private List<Asset> assets = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setAssets(List<Asset> assets) {
    this.updateDate = now();
    this.assets = assets;
  }

  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "findings_teams",
      joinColumns = @JoinColumn(name = "finding_id"),
      inverseJoinColumns = @JoinColumn(name = "team_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("finding_teams")
  @Queryable(filterable = true, dynamicValues = true, path = "teams.id")
  private List<Team> teams = new ArrayList<>();

  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "findings_users",
      joinColumns = @JoinColumn(name = "finding_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("finding_users")
  @Queryable(filterable = true, dynamicValues = true, path = "users.id")
  private List<User> users = new ArrayList<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.SIMULATION;

  @JsonProperty("finding_simulation")
  @Queryable(filterable = true, dynamicValues = true, path = "inject.exercise.id")
  public Exercise getSimulation() {
    if (getInject() == null) {
      return null;
    }
    return getInject().getExercise();
  }

  @JsonProperty("finding_scenario")
  @Queryable(filterable = true, dynamicValues = true, path = "inject.exercise.scenario.id")
  public Scenario getScenario() {
    if (getInject() == null) {
      return null;
    }
    return Optional.ofNullable(getInject().getExercise()).map(Exercise::getScenario).orElse(null);
  }

  @JsonProperty("finding_asset_groups")
  @Queryable(filterable = true, dynamicValues = true, path = "inject.assetGroups.id")
  public Set<AssetGroup> getAssetGroups() {
    if (getInject() == null) {
      return Collections.emptySet();
    }
    return getInject().getAssetGroups().stream().collect(Collectors.toSet());
  }

  // The inject's injector can be null: either the inject has no injector resolved yet, or the
  // connector that produced it was uninstalled (Inject#injector degrades to null via @NotFound
  // instead of throwing, see Inject.java). Findings created manually via the API without a real
  // inject/injector must simply show no source, not fail.
  @JsonProperty("finding_source")
  @Queryable(filterable = true, dynamicValues = true, path = "inject.injector.id", label = "source")
  public Injector getSource() {
    if (getInject() == null) {
      return null;
    }
    return getInject().getInjector();
  }
}
