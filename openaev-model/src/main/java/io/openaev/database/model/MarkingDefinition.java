package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "marking_definitions")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
// marking_definitions is a tenant-v2 active table (inspector + can_access_tenant), so no v1
// @Filter.
public class MarkingDefinition implements TenantBase {

  @Id
  @ControlledUuidGeneration
  @Column(name = "marking_definition_id")
  @JsonProperty("marking_definition_id")
  @NotBlank
  private String id;

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "marking_definition_type", nullable = false)
  @JsonProperty("marking_definition_type")
  @NotBlank
  private String type;

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "marking_definition_definition", nullable = false)
  @JsonProperty("marking_definition_definition")
  @NotBlank
  private String definition;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "marking_definition_color")
  @JsonProperty("marking_definition_color")
  private String color;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "marking_definition_order", nullable = false)
  @JsonProperty("marking_definition_order")
  @NotNull
  @Min(0)
  private Integer order = 0;

  @Column(name = "marking_definition_protected", nullable = false)
  @JsonProperty("marking_definition_protected")
  @NotNull
  private Boolean protectedDefinition = false;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "marking_definition_created_at", nullable = false)
  @JsonProperty("marking_definition_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Column(name = "marking_definition_updated_at", nullable = false)
  @JsonProperty("marking_definition_updated_at")
  @NotNull
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.MARKING_DEFINITION;

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
