package io.openaev.database.model.catalog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/** One version of a payload or scenario offered to the tenant by a catalogue release. */
@Getter
@Setter
@Entity
@Table(name = "catalog_items")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "catalog_item_type", discriminatorType = DiscriminatorType.STRING)
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
public abstract class CatalogItem implements TenantBase, Auditable {

  @Id
  @Column(name = "catalog_item_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("catalog_item_id")
  @NotBlank
  private String id;

  // -- IDENTITY --

  @Column(name = "catalog_item_type", insertable = false, updatable = false)
  @Enumerated(EnumType.STRING)
  @JsonProperty("catalog_item_type")
  @Getter(AccessLevel.NONE)
  private ContentType type;

  /** Stable id of the content in its repository, the same across versions. */
  @Column(name = "catalog_item_external_id", updatable = false)
  @JsonProperty("catalog_item_external_id")
  @NotBlank
  private String externalId;

  /** Version of the content, e.g. {@code 1.2.0}. */
  @Column(name = "catalog_item_version", updatable = false)
  @JsonProperty("catalog_item_version")
  @NotBlank
  private String version;

  /** What Filigran says of this version: deprecated and revoked versions are not offered. */
  @Column(name = "catalog_item_status")
  @Enumerated(EnumType.STRING)
  @JsonProperty("catalog_item_status")
  @NotNull
  private CatalogItemStatus status;

  // -- DESCRIPTION --

  /** Name shown in the catalogue. */
  @Column(name = "catalog_item_name")
  @JsonProperty("catalog_item_name")
  private String name;

  /** Description shown in the catalogue. */
  @Column(name = "catalog_item_description")
  @JsonProperty("catalog_item_description")
  private String description;

  /** Who wrote the content, e.g. Filigran. */
  @Column(name = "catalog_item_author")
  @JsonProperty("catalog_item_author")
  private String author;

  /** Platforms the content runs on, e.g. Windows, Linux. */
  @Type(StringArrayType.class)
  @Column(name = "catalog_item_platforms", columnDefinition = "text[]")
  @JsonProperty("catalog_item_platforms")
  private String[] platforms = new String[0];

  /** What changed in this version. */
  @Column(name = "catalog_item_changelog")
  @JsonProperty("catalog_item_changelog")
  private String changelog;

  // -- PACKAGE --

  /** File name of the archive in the release. */
  @Column(name = "catalog_item_archive_name")
  @JsonProperty("catalog_item_archive_name")
  @NotBlank
  private String archiveName;

  /** SHA-256 of the archive, checked against the manifest; also its name in file storage. */
  @Column(name = "catalog_item_archive_hash")
  @JsonProperty("catalog_item_archive_hash")
  @NotBlank
  private String archiveHash;

  // -- RELEASE --

  /** Release that brought this version; its repository identifies the item with its id. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "catalog_item_release_id", updatable = false, nullable = false)
  @JsonIgnore
  private CatalogRelease release;

  /** When Filigran published this version. */
  @Column(name = "catalog_item_published_at")
  @JsonProperty("catalog_item_published_at")
  private Instant publishedAt;

  // -- AUDIT --

  @Column(name = "catalog_item_created_at", updatable = false)
  @JsonProperty("catalog_item_created_at")
  @NotNull
  private Instant createdAt;

  @Column(name = "catalog_item_updated_at")
  @JsonProperty("catalog_item_updated_at")
  @NotNull
  private Instant updatedAt;

  // -- TENANT --

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  /** Read from the discriminator once loaded, from the subclass before the first save. */
  public ContentType getType() {
    if (type != null) {
      return type;
    }
    DiscriminatorValue value = getClass().getAnnotation(DiscriminatorValue.class);
    return value == null ? null : ContentType.valueOf(value.value());
  }

  @Override
  public boolean isListened() {
    return false;
  }
}
