package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MultiIdSetSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Setter
@Getter
@Entity
@Table(name = "platform_groups")
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
public class PlatformGroup implements Base, Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "platform_group_id")
  @JsonProperty("platform_group_id")
  @NotBlank
  private String id;

  @Queryable(searchable = true, sortable = true)
  @Column(name = "platform_group_name", nullable = false, unique = true)
  @JsonProperty("platform_group_name")
  @NotBlank
  private String name;

  @Column(name = "platform_group_description")
  @JsonProperty("platform_group_description")
  private String description;

  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "platform_groups_users",
      joinColumns = @JoinColumn(name = "platform_group_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("platform_group_users")
  @Fetch(value = FetchMode.SUBSELECT)
  private Set<User> users = new HashSet<>();

  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "platform_groups_platform_roles",
      joinColumns = @JoinColumn(name = "platform_group_id"),
      inverseJoinColumns = @JoinColumn(name = "platform_role_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("platform_group_platform_roles")
  @Fetch(value = FetchMode.SUBSELECT)
  private Set<PlatformRole> platformRoles = new HashSet<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.PLATFORM_GROUP;

  // -- AUDIT --

  @Queryable(sortable = true)
  @Column(name = "platform_group_created_at", updatable = false)
  @JsonProperty("platform_group_created_at")
  @NotNull
  private Instant createdAt;

  @Queryable(sortable = true)
  @Column(name = "platform_group_updated_at")
  @JsonProperty("platform_group_updated_at")
  @NotNull
  private Instant updatedAt;

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin() || users.contains(user);
  }

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
