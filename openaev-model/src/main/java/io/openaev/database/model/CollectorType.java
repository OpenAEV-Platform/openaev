package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Reference entity for collector type identifiers (e.g. "openaev_crowdstrike").
 *
 * <p>This table is independent of collector instances and has no cascade behavior. Rows are
 * inserted when a collector type is first registered and never deleted (even if all instances of
 * that type are removed). Detection rules survive instance lifecycle.
 */
@Entity
@Table(name = "collector_types")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CollectorType implements TenantBase {

  @Id
  @Column(name = "collector_type_id")
  @JsonProperty("collector_type_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @NotBlank
  private String id;

  @Column(name = "collector_type_name", nullable = false)
  @JsonProperty("collector_type_name")
  @NotBlank
  private String name;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  public CollectorType(String name) {
    this.name = name;
  }

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
  }
}
