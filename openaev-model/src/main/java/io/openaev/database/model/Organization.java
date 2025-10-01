package io.openaev.database.model;

import static java.time.Instant.now;
import static java.util.stream.StreamSupport.stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MultiIdListDeserializer;
import io.openaev.helper.MultiIdSetDeserializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "organizations")
@EntityListeners(ModelBaseListener.class)
public class Organization implements Base {

  @Id
  @Column(name = "organization_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("organization_id")
  @NotBlank
  private String id;

  @Column(name = "organization_name")
  @JsonProperty("organization_name")
  @Queryable(searchable = true)
  @NotBlank
  private String name;

  @Column(name = "organization_description")
  @JsonProperty("organization_description")
  private String description;

  @Column(name = "organization_created_at")
  @JsonProperty("organization_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "organization_updated_at")
  @JsonProperty("organization_updated_at")
  @NotNull
  @Queryable(sortable = true)
  private Instant updatedAt = now();

  @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
  @JsonIgnore
  private List<User> users = new ArrayList<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.ORGANIZATION;

  @ArraySchema(schema = @Schema(type = "string"))
  @Setter
  @Getter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "organizations_tags",
      joinColumns = @JoinColumn(name = "organization_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetDeserializer.class)
  @JsonProperty("organization_tags")
  private Set<Tag> tags = new HashSet<>();

  // region transient
  private transient List<Inject> injects = new ArrayList<>();

  public void resolveInjects(Iterable<Inject> injects) {
    this.injects =
        stream(injects.spliterator(), false)
            .filter(
                inject ->
                    inject.isAllTeams()
                        || inject.getTeams().stream()
                            .anyMatch(
                                team ->
                                    getUsers().stream()
                                        .flatMap(user -> user.getTeams().stream())
                                        .toList()
                                        .contains(team)))
            .collect(Collectors.toList());
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("organization_injects")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  public List<Inject> getOrganizationInject() {
    return injects;
  }

  @JsonProperty("organization_injects_number")
  public long getOrganizationInjectsNumber() {
    return injects.size();
  }

  // endregion

  public String getId() {
    return id;
  }

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public List<User> getUsers() {
    return users;
  }

  public void setUsers(List<User> users) {
    this.users = users;
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
