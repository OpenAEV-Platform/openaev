package io.openaev.context;

import io.openaev.database.model.Tenant;
import java.util.Map;
import org.springframework.data.spel.spi.EvaluationContextExtension;
import org.springframework.stereotype.Component;

@Component
public class TenantContext implements EvaluationContextExtension {

  private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

  public static String getCurrentTenant() {
    return Tenant.DEFAULT_TENANT_UUID;
    // return CURRENT_TENANT.get();
  }

  // TODO multi-tenancy: set with Front URL instead of default UUID and update the return above
  public static void setCurrentTenant(String tenant) {
    CURRENT_TENANT.set(tenant);
  }

  @Override
  public String getExtensionId() {
    return "tenantContext";
  }

  @Override
  public Map<String, Object> getProperties() {
    return Map.of("currentTenant", getCurrentTenant());
  }
}
