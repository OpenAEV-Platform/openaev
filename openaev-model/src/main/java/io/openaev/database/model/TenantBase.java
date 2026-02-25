package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public interface TenantBase {
  Tenant getTenant();

  void setTenant(Tenant tenant);
}
