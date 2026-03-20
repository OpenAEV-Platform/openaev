package io.openaev.utilstest;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class DefaultTenantExtension implements BeforeEachCallback, AfterEachCallback {

  @Override
  public void beforeEach(ExtensionContext context) {
    TenantContext.setCurrentTenant(Tenant.DEFAULT_TENANT_UUID);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    TenantContext.clearCurrentTenant();
  }
}
