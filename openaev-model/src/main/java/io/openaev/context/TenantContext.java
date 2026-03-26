package io.openaev.context;

import io.openaev.database.model.Tenant;
import java.util.Map;
import org.springframework.data.spel.spi.EvaluationContextExtension;
import org.springframework.stereotype.Component;

@Component
public class TenantContext implements EvaluationContextExtension {

  private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

  public static String getCurrentTenant() {
    String tenant = CURRENT_TENANT.get();
    return tenant != null ? tenant : Tenant.DEFAULT_TENANT_UUID;
  }

  /**
   * DO NOT USE except to set the tenant id from the URL (TenantInterceptor) AND in very specific
   * use cases before transactional annotations (like DataPack) because it could have some weird
   * behaviors inside the BackEnd
   *
   * @param tenant id
   */
  public static void setCurrentTenant(String tenant) {
    CURRENT_TENANT.set(tenant);
  }

  public static void clearCurrentTenant() {
    CURRENT_TENANT.remove();
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
