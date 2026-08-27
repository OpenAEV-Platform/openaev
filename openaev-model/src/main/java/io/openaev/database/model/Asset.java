package io.openaev.database.model;

import static jakarta.persistence.DiscriminatorType.STRING;
import static java.time.Instant.now;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.DomainConstraint;
import io.openaev.annotation.Ipv4OrIpv6Constraint;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.AuditLogIgnore;
import io.openaev.database.audit.AuditStateIgnore;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MultiIdSetSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * An {@code Asset} is any element of the customer attack surface a simulation can target. Its
 * {@link #category} - not its concrete entity type - defines its attack surface (which techniques /
 * injectors apply) and its security domain.
 *
 * <p>This concrete base holds every non-agentic target category (cloud, web application, network
 * device, IoT/OT, SaaS, identity, generic) as well as AI targets ({@code category = AI_TARGET}).
 * The only behavioral specialization is {@link Endpoint} (agent-capable hosts). {@link
 * SecurityPlatform} is a separate concept - a detection / prevention source rather than a target.
 */
@Data
@Entity
@Table(name = "assets")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "asset_type", discriminatorType = STRING)
@DiscriminatorValue(AssetType.Values.ASSET_TYPE)
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Asset implements TenantBase {

  /** Provider of an AI target ({@code category = AI_TARGET}). */
  public enum AI_TARGET_PROVIDER {
    @JsonProperty("OPENAI_COMPATIBLE")
    OPENAI_COMPATIBLE,
    @JsonProperty("ANTHROPIC")
    ANTHROPIC,
    @JsonProperty("AZURE_OPENAI")
    AZURE_OPENAI,
    @JsonProperty("AWS_BEDROCK")
    AWS_BEDROCK,
    @JsonProperty("GOOGLE_VERTEX")
    GOOGLE_VERTEX,
    @JsonProperty("HUGGINGFACE")
    HUGGINGFACE,
    @JsonProperty("OLLAMA")
    OLLAMA,
    @JsonProperty("CUSTOM_HTTP")
    CUSTOM_HTTP,
    @JsonProperty("MCP_SERVER")
    MCP_SERVER,
    @JsonProperty("AGENT_HTTP")
    AGENT_HTTP,
    @JsonProperty("XTM_ONE")
    XTM_ONE,
  }

  /** Modality of an AI target ({@code category = AI_TARGET}). */
  public enum AI_TARGET_MODALITY {
    @JsonProperty("TEXT")
    TEXT,
    @JsonProperty("VISION")
    VISION,
    @JsonProperty("AUDIO")
    AUDIO,
    @JsonProperty("MULTIMODAL")
    MULTIMODAL,
  }

  /**
   * Activity status of an asset, derived from its agents' heartbeats: {@code ACTIVE} if at least
   * one agent has been seen within the active window, {@code INACTIVE} if it has agents but none is
   * active, {@code AGENTLESS} if it has no agent at all.
   */
  public enum ASSET_ACTIVITY_STATUS {
    @JsonProperty("ACTIVE")
    ACTIVE,
    @JsonProperty("INACTIVE")
    INACTIVE,
    @JsonProperty("AGENTLESS")
    AGENTLESS,
  }

  @Id
  @Column(name = "asset_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("asset_id")
  @NotBlank
  @Queryable(filterable = true)
  private String id;

  @Column(name = "asset_type", insertable = false, updatable = false)
  @JsonProperty("asset_type")
  @Setter(NONE)
  private String type;

  @Queryable(searchable = true, sortable = true, filterable = true)
  @Column(name = "asset_name")
  @JsonProperty("asset_name")
  @NotBlank
  private String name;

  @Queryable(sortable = true)
  @Column(name = "asset_description")
  @JsonProperty("asset_description")
  private String description;

  @Queryable(searchable = true, sortable = true, filterable = true)
  @Column(name = "asset_external_reference")
  @JsonProperty("asset_external_reference")
  private String externalReference;

  // -- CATEGORIZATION --

  @Queryable(filterable = true, sortable = true)
  @Column(name = "asset_category")
  @JsonProperty("asset_category")
  @Enumerated(EnumType.STRING)
  private AssetCategory category;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "asset_subcategory")
  @JsonProperty("asset_subcategory")
  @Enumerated(EnumType.STRING)
  private AssetSubCategory subcategory;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "asset_criticality")
  @JsonProperty("asset_criticality")
  @Enumerated(EnumType.STRING)
  private AssetCriticality criticality;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "asset_internet_facing")
  @JsonProperty("asset_internet_facing")
  private Boolean internetFacing;

  // -- CLOUD (CLOUD_RESOURCE assets) --

  @Queryable(filterable = true, sortable = true)
  @Column(name = "asset_cloud_provider")
  @JsonProperty("asset_cloud_provider")
  @Enumerated(EnumType.STRING)
  private CloudProvider cloudProvider;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "asset_cloud_native_type")
  @JsonProperty("asset_cloud_native_type")
  private String cloudNativeType;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "asset_cloud_region")
  @JsonProperty("asset_cloud_region")
  private String cloudRegion;

  /**
   * For IDENTITY assets: the id of the {@link User} (person) this identity belongs to. Stored as
   * the user id with a database FK (ON DELETE SET NULL); the link surfaces the physical person
   * behind an identity asset.
   */
  @Queryable(filterable = true, dynamicValues = true)
  @Column(name = "asset_linked_person")
  @JsonProperty("asset_linked_person")
  private String linkedPerson;

  // A blank linked-person id (e.g. an empty string from a cleared form field) is normalized to
  // null so it can never violate the asset_linked_person -> users(user_id) foreign key.
  public void setLinkedPerson(String linkedPerson) {
    this.linkedPerson = (linkedPerson == null || linkedPerson.isBlank()) ? null : linkedPerson;
  }

  // -- NETWORK REACHABILITY (network-reachable categories) --
  // Moved up from Endpoint (columns + wire renamed endpoint_* -> asset_*): these are relevant to
  // more than agent hosts (web / cloud / network categories), so they live on the Asset base.

  @Queryable(filterable = true)
  @Ipv4OrIpv6Constraint
  @Type(StringArrayType.class)
  @Column(name = "asset_ips", columnDefinition = "text[]")
  @JsonProperty("asset_ips")
  private String[] ips;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "asset_seen_ip")
  @JsonProperty("asset_seen_ip")
  private String seenIp;

  @DomainConstraint
  @Queryable(filterable = true, sortable = true)
  @Column(name = "asset_hostname")
  @JsonProperty("asset_hostname")
  private String hostname;

  /** URL of the target for URL-based categories (web applications, cloud endpoints, ...). */
  @Queryable(filterable = true, sortable = true)
  @Column(name = "asset_url")
  @JsonProperty("asset_url")
  private String url;

  @Type(StringArrayType.class)
  @Column(name = "asset_mac_addresses")
  @JsonProperty("asset_mac_addresses")
  private String[] macAddresses;

  public void setHostname(String hostname) {
    // Locale.ROOT keeps hostname normalization stable regardless of the JVM default locale
    // (e.g. the Turkish dotless-i), since hostnames are not locale-specific text.
    this.hostname = (hostname == null) ? null : hostname.toLowerCase(Locale.ROOT);
  }

  // -- AI TARGET (category = AI_TARGET) --
  // Nullable, category-scoped connection attributes for AI targets. Validated by category at the
  // API layer rather than by a class-level @NotNull, since AI is a category of Asset, not a type.

  @Queryable(filterable = true, sortable = true)
  @Column(name = "ai_target_provider")
  @JsonProperty("ai_target_provider")
  @Enumerated(EnumType.STRING)
  private AI_TARGET_PROVIDER aiTargetProvider;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "ai_target_endpoint")
  @JsonProperty("ai_target_endpoint")
  private String aiTargetEndpoint;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "ai_target_model")
  @JsonProperty("ai_target_model")
  private String aiTargetModel;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "ai_target_modality")
  @JsonProperty("ai_target_modality")
  @Enumerated(EnumType.STRING)
  private AI_TARGET_MODALITY aiTargetModality;

  @Column(name = "ai_target_system_prompt")
  @JsonProperty("ai_target_system_prompt")
  private String aiTargetSystemPrompt;

  /**
   * Free-form, provider-specific configuration for an AI target (extra generation parameters,
   * custom headers, tool / MCP definitions, agent routing, ...). Never put the credential here -
   * use {@link #aiTargetToken}.
   */
  @Type(JsonType.class)
  @Column(name = "ai_target_configuration", columnDefinition = "jsonb")
  @JsonProperty("ai_target_configuration")
  private Map<String, Object> aiTargetConfiguration = new HashMap<>();

  /**
   * Optional credential used to reach an AI target, provisioned on the asset itself (set manually
   * or by a collector). May be empty for targets that require no authentication.
   */
  @Column(name = "ai_target_token")
  @JsonProperty("ai_target_token")
  private String aiTargetToken;

  /**
   * Free-form, category-specific attributes (cloud account id / resource id / ARN, vendor, model,
   * OS version, protocol, image digest, identity principal, ...). Stored as {@code jsonb} so the
   * single-table {@code SELECT DISTINCT a FROM Asset a} queries keep an equality operator.
   */
  @Type(JsonType.class)
  @Column(name = "asset_metadata", columnDefinition = "jsonb")
  @JsonProperty("asset_metadata")
  private Map<String, Object> metadata = new HashMap<>();

  // Read-only activity status derived from the agents linked to this asset (agents live on the
  // Endpoint subclass, but the correlated subquery works on the base assets table for every
  // category - non-agent assets simply resolve to AGENTLESS). Filterable so the inventory can
  // filter by status.
  @Queryable(filterable = true, sortable = true, refEnumClazz = ASSET_ACTIVITY_STATUS.class)
  @Formula(
      "(CASE"
          + " WHEN NOT EXISTS (SELECT 1 FROM agents ag WHERE ag.agent_asset = asset_id)"
          + " THEN 'AGENTLESS'"
          + " WHEN EXISTS (SELECT 1 FROM agents ag WHERE ag.agent_asset = asset_id"
          + " AND ag.agent_status = 'ACTIVE') THEN 'ACTIVE'"
          + " ELSE 'INACTIVE' END)")
  @Enumerated(EnumType.STRING)
  @JsonProperty("asset_status")
  @Schema(description = "Activity status derived from agents (ACTIVE / INACTIVE / AGENTLESS)")
  @AuditLogIgnore
  private ASSET_ACTIVITY_STATUS activityStatus;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // -- TAG --

  @Schema(implementation = String[].class)
  @Queryable(filterable = true, sortable = true, dynamicValues = true, path = "tags.id")
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "assets_tags",
      joinColumns = @JoinColumn(name = "asset_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("asset_tags")
  private Set<Tag> tags = new HashSet<>();

  // UpdatedAt is synced manually with linked objects because join-table changes do not dirty this
  // row. Only bump when contents actually change: an unconditional bump forces an UPDATE (and an
  // SSE restream) on every no-op collector upsert (#6778).
  public void setTags(Set<Tag> tags) {
    if (!Base.haveSameIds(this.tags, tags)) {
      this.updatedAt = now();
    }
    this.tags = tags;
  }

  // The schema property name falls back to the field name ("assetGroups"): this filter key backs
  // the endpoint target picker (/endpoints/targets). It is excluded from user-facing trigger
  // filters on the frontend side (TECHNICAL_FILTER_KEYS).
  @JsonIgnore
  @ManyToMany(mappedBy = "assets")
  @Queryable(filterable = true, dynamicValues = true, path = "assetGroups.id")
  @AuditStateIgnore
  private Set<AssetGroup> assetGroups = new HashSet<>();

  // -- AUDIT --

  @Column(name = "asset_created_at")
  @JsonProperty("asset_created_at")
  @NotNull
  @CreationTimestamp
  @AuditStateIgnore
  private Instant createdAt = now();

  @AuditStateIgnore
  @Column(name = "asset_updated_at")
  @JsonProperty("asset_updated_at")
  @NotNull
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.ASSET;

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public Asset() {}

  public Asset(String id, String type, String name) {
    this.name = name;
    this.id = id;
    this.type = type;
  }
}
