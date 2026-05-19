package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "roles")
@EntityListeners({ModelBaseListener.class})
public class Role implements DualScopeBase {

  @Id
  @ControlledUuidGeneration
  @Column(name = "role_id")
  @JsonProperty("role_id")
  @NotBlank
  private String id;

  @Queryable(searchable = true, sortable = true)
  @JsonProperty("role_name")
  @Column(name = "role_name")
  @NotBlank
  private String name;

  @Queryable(searchable = true)
  @Column(name = "role_description")
  @JsonProperty("role_description")
  private String description;

  @ElementCollection(targetClass = Capability.class, fetch = FetchType.EAGER)
  @JoinTable(name = "roles_capabilities", joinColumns = @JoinColumn(name = "role_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "capability")
  private Set<Capability> capabilities = new HashSet<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.GROUP_ROLE;

  @Column(name = "role_created_at")
  @JsonProperty("role_created_at")
  @NotNull
  @Schema(description = "Creation date of the role", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant createdAt = now();

  @Column(name = "role_updated_at")
  @JsonProperty("role_updated_at")
  @NotNull
  @Schema(description = "Update date of the role", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant updatedAt = now();

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false)
  @JsonIgnore
  @Nullable
  private Tenant tenant;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
