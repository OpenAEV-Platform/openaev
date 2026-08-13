package io.openaev.database.raw;

/**
 * (injector_contract_id, tenant_id) pair of an injector contract row. A forward-compatible
 * inventory shape for cascade cleanups that must sweep exactly the rows a database cascade deletes,
 * each pair's tenant swept with its own tenant-scoped call. Today {@code
 * unique_injector_contract_payload} (V4_98) guarantees a payload backs at most one contract row
 * platform-wide, so payload-driven inventories hold at most one pair; the pair shape keeps those
 * paths correct if that 1:1 constraint is ever relaxed.
 */
public interface RawContractTenant {

  String getInjector_contract_id();

  String getTenant_id();
}
