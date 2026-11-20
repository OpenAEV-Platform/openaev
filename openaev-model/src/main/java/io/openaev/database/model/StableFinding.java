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

  /**
   * Severity rank of the finding, 4 (CRITICAL) to 0 (UNKNOWN), per US.1 Breakdown 2: a CVE takes
   * the CVSS v3.1 score of the vulnerability catalog first, otherwise the severity sent by the
   * injector (a score or a label, matched case-insensitively, Informational = LOW). Anything else,
   * missing or invalid (negative, above 10, unknown label) is UNKNOWN. Every subquery column is
   * qualified: Hibernate prefixes the unqualified ones with the entity alias.
   */
  private static final String SEVERITY_RANK_SQL =
      """
      (case
        when stable_finding_type = 'CVE' and exists (
          select 1 from vulnerabilities v
          where lower(v.vulnerability_external_id) = lower(stable_finding_value)
            and v.tenant_id = tenant_id
            and v.vulnerability_cvss_v31 is not null)
        then (
          select max(case
            when v.vulnerability_cvss_v31 >= 9 then 4
            when v.vulnerability_cvss_v31 >= 7 then 3
            when v.vulnerability_cvss_v31 >= 4 then 2
            when v.vulnerability_cvss_v31 > 0 then 1
            else 0
          end)
          from vulnerabilities v
          where lower(v.vulnerability_external_id) = lower(stable_finding_value)
            and v.tenant_id = tenant_id)
        else coalesce((
          select max(case
            when lower(trim(o.finding_occurrence_observed_severity)) = 'critical' then 4
            when lower(trim(o.finding_occurrence_observed_severity)) = 'high' then 3
            when lower(trim(o.finding_occurrence_observed_severity)) = 'medium' then 2
            when lower(trim(o.finding_occurrence_observed_severity)) = 'low' then 1
            when lower(trim(o.finding_occurrence_observed_severity)) = 'informational' then 1
            when trim(o.finding_occurrence_observed_severity) ~ '^[0-9]+([.][0-9]+)?$'
              and trim(o.finding_occurrence_observed_severity)::numeric > 10 then 0
            when trim(o.finding_occurrence_observed_severity) ~ '^[0-9]+([.][0-9]+)?$'
              and trim(o.finding_occurrence_observed_severity)::numeric >= 9 then 4
            when trim(o.finding_occurrence_observed_severity) ~ '^[0-9]+([.][0-9]+)?$'
              and trim(o.finding_occurrence_observed_severity)::numeric >= 7 then 3
            when trim(o.finding_occurrence_observed_severity) ~ '^[0-9]+([.][0-9]+)?$'
              and trim(o.finding_occurrence_observed_severity)::numeric >= 4 then 2
            when trim(o.finding_occurrence_observed_severity) ~ '^[0-9]+([.][0-9]+)?$'
              and trim(o.finding_occurrence_observed_severity)::numeric > 0 then 1
            else 0
          end)
          from finding_occurrences o
          where o.finding_occurrence_stable_finding_id = stable_finding_id
            and o.tenant_id = tenant_id), 0)
      end)
      """;

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "stable_finding_id", updatable = false, nullable = false)
  @Queryable(sortable = true)
  @JsonProperty("finding_id")
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

  @JsonProperty("finding_source_type")
  @Queryable(filterable = true, path = "sourceInjector.type", label = "source type")
  public String getSourceType() {
    return sourceInjector == null ? null : sourceInjector.getType();
  }

  // -- LOCATION (US.0.0: identity = Type + Value + Location) --

  @Enumerated(EnumType.STRING)
  @Column(name = "stable_finding_location_type")
  private FindingLocationType locationType;

  /** Normalized location key: asset id, user id, team id or cloud resource path. */
  @Column(name = "stable_finding_location_key", length = 1024)
  private String locationKey;

  /** Location as displayed: asset name, user name or cloud resource. */
  @Column(name = "stable_finding_location", columnDefinition = "text")
  private String location;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stable_finding_location_asset_id")
  @Queryable(filterable = true, dynamicValues = true, path = "locationAsset.id", label = "asset")
  @JsonProperty("finding_assets")
  private Asset locationAsset;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stable_finding_location_user_id")
  private User locationUser;

  /** Asset name used by the Asset sort; findings without asset are placed last, see below. */
  @Formula(
      "(select lower(a.asset_name) from assets a where a.asset_id = stable_finding_location_asset_id)")
  @Queryable(sortable = true, label = "asset")
  @JsonProperty("finding_asset_name")
  private String locationAssetName;

  /** 1 when the finding has no asset (finding on a user or a resource): sorted first, ascending. */
  @Formula("(case when stable_finding_location_asset_id is null then 1 else 0 end)")
  @Queryable(sortable = true)
  @JsonProperty("finding_asset_missing")
  private Integer locationAssetMissing;

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

  // No column alias in these formulas: Hibernate prefixes unqualified identifiers (an "as rank"
  // alias became "as sf1_0.rank") with the entity alias, which breaks the generated SQL.
  @Formula(
      "(case "
          + SEVERITY_RANK_SQL
          + " when 4 then 'CRITICAL' when 3 then 'HIGH' when 2 then 'MEDIUM' when 1 then 'LOW'"
          + " else 'UNKNOWN' end)")
  @Queryable(filterable = true, label = "severity")
  @JsonProperty("finding_severity")
  private String latestSeverity;

  /** Sort key of the severity: descending lists CRITICAL first and UNKNOWN last. */
  @Formula(SEVERITY_RANK_SQL)
  @Queryable(sortable = true, label = "severity")
  @JsonProperty("finding_severity_rank")
  private Integer severityRank;

  @Formula(
      """
      (select case when count(distinct lower(o.finding_occurrence_resource_provider)) = 1
        then min(lower(o.finding_occurrence_resource_provider))
        else null end
       from finding_occurrences o
       where o.finding_occurrence_stable_finding_id = stable_finding_id
         and o.tenant_id = tenant_id)
      """)
  @Queryable(filterable = true, sortable = true, label = "cloud provider")
  @JsonProperty("finding_cloud_provider")
  private String cloudProvider;

  // Mirrors StableFindingMapper: the triage of the latest occurrence's legacy finding, else
  // UNTRIAGED.
  @Formula(
      """
      (coalesce((
        select t.finding_triage_status::text
        from finding_occurrences o
        left join finding_triages t
          on t.finding_triage_finding_id = o.finding_occurrence_migrated_from
         and t.tenant_id = o.tenant_id
        where o.finding_occurrence_stable_finding_id = stable_finding_id
          and o.tenant_id = tenant_id
        order by o.finding_occurrence_observed_at desc, o.finding_occurrence_id desc
        limit 1
      ), 'UNTRIAGED'))
      """)
  @Queryable(filterable = true, sortable = true, label = "triage status")
  @JsonProperty("finding_triage_status")
  private String triageStatus;

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
