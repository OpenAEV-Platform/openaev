package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/** One version of a payload or scenario offered by a content repository release (F16). */
@Getter
@Setter
@Entity
@Table(name = "catalog_items")
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
public class CatalogItem implements TenantBase, Auditable {

  @Id
  @Column(name = "catalog_item_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("catalog_item_id")
  @NotBlank
  private String id;

  @Column(name = "catalog_item_type", updatable = false)
  @Enumerated(EnumType.STRING)
  @JsonProperty("catalog_item_type")
  @NotNull
  private ContentType type;

  @Column(name = "catalog_item_external_id", updatable = false)
  @JsonProperty("catalog_item_external_id")
  @NotBlank
  private String externalId;

  @Column(name = "catalog_item_version", updatable = false)
  @JsonProperty("catalog_item_version")
  @NotBlank
  private String version;

  // VERIFIED, DEPRECATED or REVOKED, from the manifest; only drives the catalogue (F31).
  @Column(name = "catalog_item_status")
  @JsonProperty("catalog_item_status")
  private String status;

  @Column(name = "catalog_item_name")
  @JsonProperty("catalog_item_name")
  private String name;

  @Column(name = "catalog_item_description")
  @JsonProperty("catalog_item_description")
  private String description;

  @Column(name = "catalog_item_author")
  @JsonProperty("catalog_item_author")
  private String author;

  // -- FACETS --

  @Column(name = "catalog_item_severity")
  @JsonProperty("catalog_item_severity")
  private String severity;

  @Column(name = "catalog_item_category")
  @JsonProperty("catalog_item_category")
  private String category;

  @Column(name = "catalog_item_main_focus")
  @JsonProperty("catalog_item_main_focus")
  private String mainFocus;

  @Column(name = "catalog_item_scenario_type")
  @JsonProperty("catalog_item_scenario_type")
  private String scenarioType;

  @Type(StringArrayType.class)
  @Column(name = "catalog_item_platforms", columnDefinition = "text[]")
  @JsonProperty("catalog_item_platforms")
  private String[] platforms = new String[0];

  @Column(name = "catalog_item_injects_count")
  @JsonProperty("catalog_item_injects_count")
  private Integer injectsCount;

  @Column(name = "catalog_item_changelog")
  @JsonProperty("catalog_item_changelog")
  private String changelog;

  // -- PACKAGE --

  // Payloads a scenario references, as a JSON array of {external_id, version, sha256}.
  @Column(name = "catalog_item_payload_refs")
  @JsonIgnore
  private String payloadRefs;

  @Column(name = "catalog_item_archive_name")
  @JsonProperty("catalog_item_archive_name")
  private String archiveName;

  // SHA-256 of the archive, checked against the manifest; also its storage key.
  @Column(name = "catalog_item_archive_hash")
  @JsonProperty("catalog_item_archive_hash")
  private String archiveHash;

  @Column(name = "catalog_item_repository")
  @JsonProperty("catalog_item_repository")
  private String repository;

  @Column(name = "catalog_item_release")
  @JsonProperty("catalog_item_release")
  private String release;

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

  @Override
  public boolean isListened() {
    return false;
  }
}
