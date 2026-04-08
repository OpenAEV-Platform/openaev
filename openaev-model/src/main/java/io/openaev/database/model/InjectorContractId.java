package io.openaev.database.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key for {@link InjectorContract}.
 *
 * <p>Since built-in injector contracts have static IDs (e.g. EMAIL_DEFAULT, CHALLENGE_PUBLISH),
 * each tenant needs its own copy. The PK must therefore include both the contract ID and the tenant
 * ID.
 *
 * <p>The tenant is stored as a plain {@code String} (not a {@code @ManyToOne}) to avoid a known
 * Hibernate 6.x {@code AssertionError} in {@code EmbeddableAssembler} when an {@code @EmbeddedId}
 * with {@code @ManyToOne} is combined with EAGER {@code @ManyToMany} collections using composite
 * join columns.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class InjectorContractId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "injector_contract_id")
  private String id;

  @Column(name = "tenant_id", updatable = false, nullable = false)
  private String tenantId;

  public InjectorContractId(String id, String tenantId) {
    this.id = id;
    this.tenantId = tenantId;
  }

  /** Convenience constructor that extracts the tenant ID from a {@link Tenant} entity. */
  public InjectorContractId(String id, Tenant tenant) {
    this(id, tenant != null ? tenant.getId() : null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InjectorContractId that = (InjectorContractId) o;
    return Objects.equals(id, that.id) && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, tenantId);
  }
}
