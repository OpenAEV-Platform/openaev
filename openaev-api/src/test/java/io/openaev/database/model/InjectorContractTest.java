package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InjectorContractTest {

  private static Injector injector(String id, String type, String tenantId) {
    Injector injector = new Injector();
    injector.setId(id);
    injector.setName(id);
    injector.setType(type);
    injector.setTenantId(tenantId);
    return injector;
  }

  private static InjectorContract contractInTenant(String tenantId) {
    InjectorContract contract = new InjectorContract();
    contract.setTenant(new Tenant(tenantId));
    return contract;
  }

  @Test
  @DisplayName("addInjector rejects an injector from a different tenant than the contract")
  void addInjector_rejects_cross_tenant_injector() {
    // The join row's tenant is derived from the injector, so linking a foreign-tenant injector
    // would
    // silently write the link into the wrong tenant. The invariant must fail loud instead.
    InjectorContract contract = contractInTenant("tenant-A");

    assertThatThrownBy(() -> contract.addInjector(injector("email", "email", "tenant-B")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenant");
    assertThat(contract.getInjectors()).isEmpty();
  }

  @Test
  @DisplayName("addInjector accepts an injector from the same tenant as the contract")
  void addInjector_accepts_same_tenant_injector() {
    InjectorContract contract = contractInTenant("tenant-A");

    contract.addInjector(injector("email", "email", "tenant-A"));

    assertThat(contract.getInjectors()).extracting(Injector::getId).containsExactly("email");
  }
}
