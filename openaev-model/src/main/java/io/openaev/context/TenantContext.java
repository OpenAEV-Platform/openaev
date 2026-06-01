package io.openaev.context;

import java.util.Map;
import org.springframework.data.spel.spi.EvaluationContextExtension;
import org.springframework.stereotype.Component;

@Component
public class TenantContext implements EvaluationContextExtension {

  private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

  public static String getCurrentTenant() {
    String tenant = CURRENT_TENANT.get();
    if (tenant == null) {
      throw new IllegalArgumentException("Tenant should not be null");
    }
    return tenant;
  }

  /** Returns true if a tenant has been set on the current thread. */
  public static boolean hasCurrentTenant() {
    return CURRENT_TENANT.get() != null;
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
