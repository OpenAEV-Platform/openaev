package io.openbas.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openbas.annotation.Queryable;
import io.openbas.database.audit.ModelBaseListener;
import io.openbas.helper.MultiIdListDeserializer;
import io.openbas.helper.MultiModelDeserializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Getter
@Entity
@Table(name = "groups")
@EntityListeners(ModelBaseListener.class)
public class Group implements Base {

  @Id
  @Column(name = "group_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("group_id")
  @NotBlank
  private String id;

  @Queryable(searchable = true, sortable = true)
  @Column(name = "group_name")
  @JsonProperty("group_name")
  @NotBlank
  private String name;

  @Column(name = "group_description")
  @JsonProperty("group_description")
  private String description;

  @Queryable(sortable = true)
  @Column(name = "group_default_user_assign")
  @JsonProperty("group_default_user_assign")
  private boolean defaultUserAssignation;

  @OneToMany(
      mappedBy = "group",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("group_grants")
  @JsonSerialize(using = MultiModelDeserializer.class)
  @Fetch(value = FetchMode.SUBSELECT)
  private List<Grant> grants = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "users_groups",
      joinColumns = @JoinColumn(name = "group_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("group_users")
  @Fetch(value = FetchMode.SUBSELECT)
  private List<User> users = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "groups_organizations",
      joinColumns = @JoinColumn(name = "group_id"),
      inverseJoinColumns = @JoinColumn(name = "organization_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("group_organizations")
  private List<Organization> organizations = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "groups_roles",
      joinColumns = @JoinColumn(name = "group_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("group_roles")
  private List<Role> roles = new ArrayList<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.USER_GROUP;

  // endregion

  @Override
  public boolean isUserHasAccess(User user) {
    return users.contains(user);
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
