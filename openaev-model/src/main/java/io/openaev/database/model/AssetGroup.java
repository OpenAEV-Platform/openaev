package io.openaev.database.model;

import static java.time.Instant.now;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.Filters.FilterGroup;
import io.openaev.helper.MultiIdListDeserializer;
import io.openaev.helper.MultiIdSetDeserializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.Data;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Entity
@Table(name = "asset_groups")
@EntityListeners(ModelBaseListener.class)
@NamedEntityGraphs({
  @NamedEntityGraph(
      name = "AssetGroup.tags-assets",
      attributeNodes = {@NamedAttributeNode("tags"), @NamedAttributeNode("assets")})
})
public class AssetGroup implements Base {

  public Map<Endpoint.PLATFORM_TYPE, Map<Endpoint.PLATFORM_ARCH, String>> wellKnown() {
    return Map.of(
        Endpoint.PLATFORM_TYPE.Linux,
            Map.of(
                Endpoint.PLATFORM_ARCH.x86_64, "9cf4d8c4-9b61-4a35-ad6a-f081e1d0ef95",
                Endpoint.PLATFORM_ARCH.arm64, "a3e8763a-5ca1-432c-8513-e553d95d9dbc"),
        Endpoint.PLATFORM_TYPE.Windows,
            Map.of(
                Endpoint.PLATFORM_ARCH.x86_64, "a3fa9b8e-3b57-434d-baf1-bc44362525fd",
                Endpoint.PLATFORM_ARCH.arm64, "1585a3f8-190a-4f71-bf62-56bff624d011"),
        Endpoint.PLATFORM_TYPE.MacOS,
            Map.of(
                Endpoint.PLATFORM_ARCH.x86_64, "4faaf01d-b340-480c-a25d-026827de3846",
                Endpoint.PLATFORM_ARCH.arm64, "431b76a8-4d3d-4002-a7c1-058a69dffb81"));
  }

  @Id
  @ControlledUuidGeneration
  @Column(name = "asset_group_id")
  @JsonProperty("asset_group_id")
  @NotBlank
  private String id;

  @Column(name = "asset_group_name")
  @JsonProperty("asset_group_name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  @NotBlank
  private String name;

  @Column(name = "asset_group_description")
  @JsonProperty("asset_group_description")
  @Queryable(filterable = true, sortable = true)
  private String description;

  @Column(name = "asset_group_external_reference")
  @JsonProperty("asset_group_external_reference")
  private String externalReference;

  // -- ASSET --

  @Type(JsonType.class)
  @Column(name = "asset_group_dynamic_filter")
  @JsonProperty("asset_group_dynamic_filter")
  @NotNull
  private FilterGroup dynamicFilter = FilterGroup.defaultFilterGroup();

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "asset_groups_assets",
      joinColumns = @JoinColumn(name = "asset_group_id"),
      inverseJoinColumns = @JoinColumn(name = "asset_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("asset_group_assets")
  private List<Asset> assets = new ArrayList<>();

  @Getter(NONE)
  @Transient
  @JsonProperty("asset_group_dynamic_assets")
  private List<Asset> dynamicAssets = new ArrayList<>();

  // Getter is Mandatory when we use @Transient annotation
  @ArraySchema(schema = @Schema(type = "string"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  public List<Asset> getDynamicAssets() {
    return this.dynamicAssets;
  }

  // -- TAG --

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "asset_groups_tags",
      joinColumns = @JoinColumn(name = "asset_group_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetDeserializer.class)
  @JsonProperty("asset_group_tags")
  @Queryable(filterable = true, sortable = true, dynamicValues = true, path = "tags.id")
  private Set<Tag> tags = new HashSet<>();

  // -- INJECT --

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "injects_asset_groups",
      joinColumns = @JoinColumn(name = "asset_group_id"),
      inverseJoinColumns = @JoinColumn(name = "inject_id"))
  @JsonProperty("asset_group_injects")
  @JsonIgnore
  @Queryable(filterable = true, dynamicValues = true, path = "injects.id")
  private List<Inject> injects = new ArrayList<>();

  // -- AUDIT --

  @Column(name = "asset_group_created_at")
  @JsonProperty("asset_group_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Column(name = "asset_group_updated_at")
  @JsonProperty("asset_group_updated_at")
  @NotNull
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.ASSET_GROUP;

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
