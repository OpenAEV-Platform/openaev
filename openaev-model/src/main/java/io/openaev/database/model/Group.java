package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.AuditDiffTracked;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MultiIdListSerializer;
import io.openaev.helper.MultiModelSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Setter
@Getter
@Entity
@Table(name = "groups")
@EntityListeners({ModelBaseListener.class})
@AuditDiffTracked
public class Group implements DualScopeBase {

  @Id
  @ControlledUuidGeneration
  @Column(name = "group_id")
  @JsonProperty("group_id")
  @NotBlank
  private String id;

  @Queryable(searchable = true, sortable = true)
  @Column(name = "group_name")
  @JsonProperty("group_name")
  @NotBlank
  private String name;

  @Queryable(searchable = true)
  @Column(name = "group_description")
  @JsonProperty("group_description")
  private String description;

  @Column(name = "group_default_user_assign")
  @JsonProperty("group_default_user_assign")
  private boolean defaultUserAssignation;

  @OneToMany(
      mappedBy = "group",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("group_grants")
  @JsonSerialize(using = MultiModelSerializer.class)
  @Fetch(value = FetchMode.SUBSELECT)
  private List<Grant> grants = new ArrayList<>();

  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "users_groups",
      joinColumns = @JoinColumn(name = "group_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("group_users")
  @Fetch(value = FetchMode.SUBSELECT)
  private List<User> users = new ArrayList<>();

  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "groups_roles",
      joinColumns = @JoinColumn(name = "group_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("group_roles")
  @Fetch(value = FetchMode.SUBSELECT)
  private List<Role> roles = new ArrayList<>();

  /**
   * The markings this group <b>grants</b> its members — the source of their clearance.
   *
   * <p>Mapped even though the clearance <i>read</i> path deliberately does not use JPA: {@code
   * MarkingClearanceCacheManager} runs before any transaction exists and must not pin a Hibernate
   * session, so it queries {@code groups_markings} with {@code JdbcTemplate}. The <b>write</b> path
   * has no such constraint — it runs inside a normal transactional service — so it uses the ORM
   * like every other group association. The two agree on the table, not on the access mechanism.
   *
   * <p>🔴 A change here changes what every member of the group may see, so every write must be
   * followed by {@code MarkingClearanceCacheManager#evictForUsers}: the cached clearance is pure
   * set containment and never re-consults this table, so a stale entry fails <b>open</b>.
   *
   * <p>EAGER + SUBSELECT to match {@link #users} and {@link #roles}: one extra query per batch of
   * groups, and the collection is serialized with the group.
   */
  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "groups_markings",
      joinColumns = @JoinColumn(name = "group_id"),
      inverseJoinColumns = @JoinColumn(name = "marking_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("group_markings")
  @Fetch(value = FetchMode.SUBSELECT)
  private List<MarkingDefinition> markings = new ArrayList<>();

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false)
  @JsonIgnore
  @Nullable
  private Tenant tenant;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.USER_GROUP;

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
