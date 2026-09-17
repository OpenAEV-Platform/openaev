package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.jsonapi.BusinessId;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JoinColumnOrFormula;
import org.hibernate.annotations.JoinColumnsOrFormulas;
import org.hibernate.annotations.JoinFormula;
import org.hibernate.annotations.UuidGenerator;

/** Stable tenant/source/type/value identity shared by repeated observations across Locations. */
@Getter
@Setter
@Entity
@Table(
    name = "stable_findings",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_stable_findings_key_tenant",
            columnNames = {"tenant_id", "stable_finding_key"}))
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class, AuditableListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class StableFinding implements TenantBase, Auditable {

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "stable_finding_id", updatable = false, nullable = false)
  @NotBlank
  private String id;

  @BusinessId
  @Column(name = "stable_finding_key", updatable = false, nullable = false, length = 64)
  @NotBlank
  private String key;

  @BusinessId
  @Queryable(searchable = true, filterable = true, sortable = true)
  @JsonProperty("finding_field")
  @Column(
      name = "stable_finding_source_namespace",
      updatable = false,
      nullable = false,
      columnDefinition = "text")
  @NotBlank
  private String sourceNamespace;

  @Column(
      name = "stable_finding_contract_output_key",
      updatable = false,
      nullable = false,
      length = 255)
  @NotBlank
  private String contractOutputKey;

  @ManyToOne(fetch = FetchType.LAZY)
  @Queryable(filterable = true, dynamicValues = true, path = "sourceInjector.id", label = "source")
  @JsonProperty("finding_source")
  @JoinColumnsOrFormulas({
    @JoinColumnOrFormula(
        column =
            @JoinColumn(
                name = "stable_finding_source_injector_id",
                referencedColumnName = "injector_id")),
    @JoinColumnOrFormula(
        formula = @JoinFormula(value = "tenant_id", referencedColumnName = "tenant_id"))
  })
  private Injector sourceInjector;

  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true, label = "finding type")
  @JsonProperty("finding_type")
  @Column(name = "stable_finding_type", updatable = false, nullable = false)
  @NotNull
  private ContractOutputType type;

  @BusinessId
  @Queryable(searchable = true, filterable = true, sortable = true)
  @JsonProperty("finding_value")
  @Column(
      name = "stable_finding_value",
      updatable = false,
      nullable = false,
      columnDefinition = "text")
  @NotBlank
  private String value;

  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true)
  @JsonProperty("finding_category")
  @Column(name = "stable_finding_category", nullable = false)
  @NotNull
  private StableFindingCategory category;

  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true, label = "aggregation category")
  @JsonProperty("finding_aggregation_category")
  @Column(name = "stable_finding_aggregation_category", nullable = false)
  @NotNull
  private FindingAggregationCategory aggregationCategory;

  @Queryable(filterable = true, sortable = true, label = "created at")
  @JsonProperty("finding_created_at")
  @Column(name = "stable_finding_first_seen", nullable = false)
  @NotNull
  private Instant firstSeen;

  @Queryable(filterable = true, sortable = true, label = "updated at")
  @JsonProperty("finding_updated_at")
  @Column(name = "stable_finding_last_seen", nullable = false)
  @NotNull
  private Instant lastSeen;

  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true)
  @JsonProperty("finding_lifecycle")
  @Column(name = "stable_finding_lifecycle", nullable = false)
  @NotNull
  private StableFindingLifecycle lifecycle = StableFindingLifecycle.ACTIVE;

  @Queryable(filterable = true, sortable = true, label = "human updated at")
  @JsonProperty("finding_human_updated_at")
  @Column(name = "stable_finding_human_updated_at")
  private Instant humanUpdatedAt;

  @JsonProperty("finding_archived_at")
  @Column(name = "stable_finding_archived_at")
  private Instant archivedAt;

  @Column(name = "stable_finding_soft_deleted_at")
  private Instant softDeletedAt;

  @Formula("(stable_finding_archived_at is not null)")
  @Queryable(filterable = true, label = "archived")
  @JsonProperty("finding_archived")
  private boolean archived;

  @Formula(
      """
      (select o.finding_occurrence_observed_severity
       from finding_occurrences o
       where o.finding_occurrence_stable_finding_id = stable_finding_id
         and o.tenant_id = tenant_id
       order by o.finding_occurrence_observed_at desc, o.finding_occurrence_id desc
       limit 1)
      """)
  @Queryable(filterable = true, sortable = true, label = "severity")
  @JsonProperty("finding_severity")
  private String latestSeverity;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "stable_findings_tags",
      joinColumns = @JoinColumn(name = "stable_finding_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @Fetch(FetchMode.SUBSELECT)
  private Set<Tag> tags = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  @NotNull
  private Tenant tenant;

  @Column(name = "stable_finding_created_at", updatable = false, nullable = false)
  @NotNull
  private Instant createdAt = now();

  @Column(name = "stable_finding_updated_at", nullable = false)
  @NotNull
  private Instant updatedAt = now();

  @Override
  public boolean isListened() {
    return false;
  }
}
