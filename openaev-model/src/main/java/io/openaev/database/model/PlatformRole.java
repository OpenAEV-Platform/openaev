package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
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
@Table(name = "platform_roles")
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
public class PlatformRole implements Base, Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "platform_role_id")
  @JsonProperty("platform_role_id")
  @NotBlank
  private String id;

  @Queryable(searchable = true, sortable = true)
  @Column(name = "platform_role_name", nullable = false, unique = true)
  @JsonProperty("platform_role_name")
  @NotBlank
  private String name;

  @Column(name = "platform_role_description")
  @JsonProperty("platform_role_description")
  @Queryable(searchable = true)
  private String description;

  @ElementCollection(targetClass = Capability.class, fetch = FetchType.EAGER)
  @JoinTable(
      name = "platform_roles_capabilities",
      joinColumns = @JoinColumn(name = "platform_role_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "capability")
  @JsonProperty("platform_role_capabilities")
  private Set<Capability> capabilities = new HashSet<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.PLATFORM_ROLE;

  // -- AUDIT --

  @Queryable(sortable = true)
  @Column(name = "platform_role_created_at", updatable = false)
  @JsonProperty("platform_role_created_at")
  @NotNull
  private Instant createdAt;

  @Queryable(sortable = true)
  @Column(name = "platform_role_updated_at")
  @JsonProperty("platform_role_updated_at")
  @NotNull
  private Instant updatedAt;

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
