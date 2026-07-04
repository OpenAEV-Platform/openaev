package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.context.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

/**
 * Join entity for the {@code injectors_injector_contracts} table linking an {@link Injector} to an
 * {@link InjectorContract}. Both endpoints have a composite {@code (id, tenant_id)} primary key and
 * share the single {@code tenant_id} column of the join table, which a {@code @ManyToMany}
 * collection cannot express (Hibernate forbids the same column in both join groups of a
 * collection). Mapping the table as an entity with two {@code @ManyToOne} lets {@code tenant_id} be
 * written once (the {@code @Id} column) and referenced read-only by each association, so both sides
 * resolve to a full composite key.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "injectors_injector_contracts")
@IdClass(InjectorInjectorContractId.class)
public class InjectorInjectorContract {

  @Id
  @Column(name = "injector_id")
  private String injectorId;

  @Id
  @Column(name = "injector_contract_id")
  private String injectorContractId;

  @Id
  @Column(name = "tenant_id")
  private String tenantId;

  @NotFound(action = NotFoundAction.IGNORE)
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumns({
    @JoinColumn(
        name = "injector_id",
        referencedColumnName = "injector_id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "tenant_id",
        referencedColumnName = "tenant_id",
        insertable = false,
        updatable = false)
  })
  @JsonIgnore
  private Injector injector;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumns({
    @JoinColumn(
        name = "injector_contract_id",
        referencedColumnName = "injector_contract_id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "tenant_id",
        referencedColumnName = "tenant_id",
        insertable = false,
        updatable = false)
  })
  @JsonIgnore
  private InjectorContract injectorContract;

  public InjectorInjectorContract(Injector injector, InjectorContract injectorContract) {
    this.injector = injector;
    this.injectorContract = injectorContract;
    this.injectorId = injector.getId();
    this.injectorContractId = injectorContract.getId();
    this.tenantId =
        injector.getTenantId() != null ? injector.getTenantId() : TenantContext.getCurrentTenant();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InjectorInjectorContract that)) {
      return false;
    }
    return Objects.equals(injectorId, that.injectorId)
        && Objects.equals(injectorContractId, that.injectorContractId)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(injectorId, injectorContractId, tenantId);
  }
}
