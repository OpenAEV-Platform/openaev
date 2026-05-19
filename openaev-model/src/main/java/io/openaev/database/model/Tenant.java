package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;

@Data
@Entity
@Table(name = "tenants")
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
public class Tenant implements Base, Auditable {

  // Same default ID for XTM HUB and OpenAEV instances
  public static String DEFAULT_TENANT_UUID = "2cffad3a-0001-4078-b0e2-ef74274022c3";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "tenant_id", nullable = false)
  @NotBlank
  @JsonProperty("tenant_id")
  @Schema(description = "Tenant ID")
  private String id;

  @Column(name = "tenant_name", nullable = false, unique = true)
  @NotBlank
  @JsonProperty("tenant_name")
  @Schema(description = "Tenant name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  private String name;

  @Column(name = "tenant_description")
  @JsonProperty("tenant_description")
  @Schema(description = "Tenant description")
  private String description;

  // -- AUDIT --

  @Column(name = "tenant_created_at", updatable = false)
  @NotNull
  @JsonProperty("tenant_created_at")
  private Instant createdAt;

  @Column(name = "tenant_updated_at")
  @NotNull
  @JsonProperty("tenant_updated_at")
  private Instant updatedAt;

  @Column(name = "tenant_deleted_at")
  @JsonProperty("tenant_deleted_at")
  private Instant deletedAt;

  public Tenant() {}

  public Tenant(String id) {
    this.id = id;
  }
}
