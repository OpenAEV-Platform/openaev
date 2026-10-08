package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** One install, update, rollback, copy, version change or uninstall of catalogue content. */
@Getter
@Setter
@Entity
@Table(name = "content_history")
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
public class ContentHistory implements TenantBase, Auditable {

  public enum ACTION {
    INSTALL,
    UPDATE,
    ROLLBACK,
    DUPLICATE,
    VERSION_CHANGE,
    UNINSTALL
  }

  @Id
  @Column(name = "content_history_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("content_history_id")
  @NotBlank
  private String id;

  // -- CONTENT --
  // Snapshot of the item: a published version never changes.

  @Column(name = "content_history_type", updatable = false)
  @Enumerated(EnumType.STRING)
  @JsonProperty("content_history_type")
  @NotNull
  private ContentType type;

  @Column(name = "content_history_external_id", updatable = false)
  @JsonProperty("content_history_external_id")
  private String externalId;

  @Column(name = "content_history_version", updatable = false)
  @JsonProperty("content_history_version")
  private String version;

  // -- ACTION --

  @Column(name = "content_history_action", updatable = false)
  @Enumerated(EnumType.STRING)
  @JsonProperty("content_history_action")
  @NotNull
  private ACTION action;

  @Column(name = "content_history_from_version", updatable = false)
  @JsonProperty("content_history_from_version")
  private String fromVersion;

  @Column(name = "content_history_automatic", updatable = false)
  @JsonProperty("content_history_automatic")
  private boolean automatic;

  // No foreign key: an uninstall can delete the entity, its history stays.
  @Column(name = "content_history_entity_id", updatable = false)
  @JsonProperty("content_history_entity_id")
  @NotBlank
  private String entityId;

  // -- AUDIT --

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_history_user", updatable = false)
  @JsonProperty("content_history_user")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private User user;

  @Column(name = "content_history_created_at", updatable = false)
  @JsonProperty("content_history_created_at")
  @NotNull
  private Instant createdAt;

  @Column(name = "content_history_updated_at")
  @JsonProperty("content_history_updated_at")
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
