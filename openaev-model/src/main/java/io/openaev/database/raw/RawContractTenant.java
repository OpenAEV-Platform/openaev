package io.openaev.database.raw;

/**
 * (injector_contract_id, tenant_id) pair of an injector contract row. Used by cross-tenant cascade
 * inventories: default contracts are provisioned id-for-id into every tenant while keeping the
 * payload FK of the original row, so a payload deletion cascades contract rows across tenants and
 * each affected tenant must be swept separately.
 */
public interface RawContractTenant {

  String getInjector_contract_id();

  String getTenant_id();
}
