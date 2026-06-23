package io.openaev.database.model;

import static jakarta.persistence.DiscriminatorType.STRING;
import static java.time.Instant.now;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.Queryable;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "assets")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "asset_type", discriminatorType = STRING)
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Asset implements TenantBase {

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
  @Queryable(filterable = true)
  @Column(name = "asset_linked_person")
  @JsonProperty("asset_linked_person")
  private String linkedPerson;

  // A blank linked-person id (e.g. an empty string from a cleared form field) is normalized to
  // null so it can never violate the asset_linked_person -> users(user_id) foreign key.
  public void setLinkedPerson(String linkedPerson) {
    this.linkedPerson = (linkedPerson == null || linkedPerson.isBlank()) ? null : linkedPerson;
  }

  /**
   * Free-form, category-specific attributes (cloud account id / resource id / ARN, vendor, model,
   * OS version, protocol, image digest, identity principal, ...). Stored as {@code jsonb} so the
   * single-table {@code SELECT DISTINCT a FROM Asset a} queries keep an equality operator.
   */
  @Type(JsonType.class)
  @Column(name = "asset_metadata", columnDefinition = "jsonb")
  @JsonProperty("asset_metadata")
  private Map<String, Object> metadata = new HashMap<>();

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

  // UpdatedAt now used to sync with linked object
  public void setTags(Set<Tag> tags) {
    this.updatedAt = now();
    this.tags = tags;
  }

  @JsonIgnore
  @ManyToMany(mappedBy = "assets")
  @Queryable(filterable = true, dynamicValues = true, path = "assetGroups.id")
  private Set<AssetGroup> assetGroups = new HashSet<>();

  // -- AUDIT --

  @Column(name = "asset_created_at")
  @JsonProperty("asset_created_at")
  @NotNull
  @CreationTimestamp
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
