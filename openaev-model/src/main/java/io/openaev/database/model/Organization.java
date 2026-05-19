package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MultiIdListSerializer;
import io.openaev.helper.MultiIdSetSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "organizations")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Organization implements TenantBase {

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

  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "organizations_tags",
      joinColumns = @JoinColumn(name = "organization_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("organization_tags")
  private Set<Tag> tags = new HashSet<>();

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // region transient
  private transient List<Inject> injects = new ArrayList<>();

  @Schema(implementation = String[].class)
  @JsonProperty("organization_injects")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<Inject> getOrganizationInject() {
    return injects;
  }

  @JsonProperty("organization_injects_number")
  public long getOrganizationInjectsNumber() {
    return injects.size();
  }

  // endregion

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
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
