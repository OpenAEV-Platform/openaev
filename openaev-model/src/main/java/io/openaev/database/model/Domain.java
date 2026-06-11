package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.jsonapi.BusinessId;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "domains")
@EntityListeners({ModelBaseListener.class})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Domain implements TenantBase {

  /** Creates a new instance scoped to the given tenant. */
  public static Domain fromTenant(String tenantId) {
    Domain entity = new Domain();
    entity.setTenant(new Tenant(tenantId));
    return entity;
  }

  public static Domain createStatic(String name, String color) {
    Domain domain = new Domain();
    domain.setName(name);
    domain.setColor(color);
    return domain;
  }

  @Id
  @Column(name = "domain_id")
  @JsonProperty("domain_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @NotBlank
  private String id;

  @Column(name = "domain_name")
  @JsonProperty("domain_name")
  @NotBlank
  @BusinessId
  private String name;

  @Column(name = "domain_color")
  @JsonProperty("domain_color")
  @NotBlank
  private String color;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @CreationTimestamp
  @Queryable(filterable = true, sortable = true, label = "created at")
  @Column(name = "domain_created_at", updatable = false)
  @JsonProperty("domain_created_at")
  private Instant creationDate = now();

  @UpdateTimestamp
  @Queryable(filterable = true, sortable = true, label = "updated at")
  @Column(name = "domain_updated_at")
  @JsonProperty("domain_updated_at")
  private Instant updateDate = now();

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }

    if (obj.getClass() != this.getClass()) {
      return false;
    }

    return this.getName().equals(((Domain) obj).getName());
  }

  public Domain copy() {
    Domain domain = new Domain();
    domain.setId(this.id);
    domain.setName(this.name);
    domain.setColor(this.color);
    domain.setTenant(this.tenant);
    domain.setCreationDate(this.creationDate);
    domain.setUpdateDate(this.updateDate);
    return domain;
  }
}
