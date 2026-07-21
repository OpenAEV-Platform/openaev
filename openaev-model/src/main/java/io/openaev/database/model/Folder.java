package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A folder used to organize files (documents) into a tenant-scoped tree. A null {@code parent}
 * means the folder sits at the root.
 */
@Setter
@Getter
@Entity
@Table(name = "folders")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Folder implements TenantBase {

  @Id
  @Column(name = "folder_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("folder_id")
  @NotBlank
  private String id;

  @Column(name = "folder_name")
  @JsonProperty("folder_name")
  @Queryable(searchable = true, sortable = true)
  @NotBlank
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "folder_parent")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("folder_parent")
  @Schema(implementation = String.class)
  private Folder parent;

  @Column(name = "folder_created_at")
  @JsonProperty("folder_created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "folder_updated_at")
  @JsonProperty("folder_updated_at")
  private Instant updatedAt = Instant.now();

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
