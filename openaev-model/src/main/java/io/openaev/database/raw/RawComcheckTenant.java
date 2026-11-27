package io.openaev.database.raw;

/** (comcheck_id, tenant_id) pair of a comcheck, the tenant being its simulation's. */
public interface RawComcheckTenant {

  String getComcheck_id();

  String getTenant_id();
}
