package io.openaev.database.raw;

/**
 * (injector_contract_id, tenant_id) pair of an injector contract row. A forward-compatible
 * inventory shape for cascade cleanups that must sweep exactly the rows a database cascade deletes,
 * each pair's tenant swept with its own tenant-scoped call. A payload belongs to one tenant and
 * {@code injector_contract_payload_unique} allows one contract per payload per tenant, so a
 * payload-driven inventory normally holds one pair; nothing in the schema stops a contract of
 * another tenant from referencing the same payload, and the pair shape keeps those paths correct
 * when one does.
 */
public interface RawContractTenant {

  String getInjector_contract_id();

  String getTenant_id();
}
