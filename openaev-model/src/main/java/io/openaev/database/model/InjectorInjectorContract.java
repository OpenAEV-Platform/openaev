package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    // Every link is created here (InjectorContract.addInjector and Injector.linkContract). The
    // link takes the injector's tenant, else the contract's, and is refused when neither names
    // one. An injector and a contract of two different tenants are refused too.
    String injectorTenantId = injector.getTenantId();
    String contractTenantId =
        injectorContract.getTenant() != null ? injectorContract.getTenant().getId() : null;
    if (injectorTenantId != null
        && contractTenantId != null
        && !injectorTenantId.equals(contractTenantId)) {
      throw new IllegalArgumentException(
          "Cannot link injector of tenant "
              + injectorTenantId
              + " to injector contract of tenant "
              + contractTenantId);
    }
    this.injector = injector;
    this.injectorContract = injectorContract;
    this.injectorId = injector.getId();
    this.injectorContractId = injectorContract.getId();
    this.tenantId = injectorTenantId != null ? injectorTenantId : contractTenantId;
    if (this.tenantId == null) {
      throw new IllegalStateException(
          "Cannot create an InjectorInjectorContract: neither the injector "
              + injector.getId()
              + " nor the injector contract "
              + injectorContract.getId()
              + " names a tenant");
    }
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
