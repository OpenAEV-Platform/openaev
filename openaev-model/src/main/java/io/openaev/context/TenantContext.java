package io.openaev.context;

import io.openaev.database.model.Tenant;
import java.util.Map;
import org.springframework.data.spel.spi.EvaluationContextExtension;
import org.springframework.stereotype.Component;

@Component
public class TenantContext implements EvaluationContextExtension {

  private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> RLS_BYPASS =
      ThreadLocal.withInitial(() -> Boolean.FALSE);

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

  /**
   * Returns {@code true} when the current thread should bypass Row-Level Security. Used by
   * scheduled jobs that need cross-tenant data access.
   */
  public static boolean isRlsBypassed() {
    return Boolean.TRUE.equals(RLS_BYPASS.get());
  }

  /**
   * Enable RLS bypass for the current thread. Must be paired with {@link #clearRlsBypass()} in a
   * {@code finally} block.
   */
  public static void setRlsBypass() {
    RLS_BYPASS.set(Boolean.TRUE);
  }

  /** Disable RLS bypass for the current thread. */
  public static void clearRlsBypass() {
    RLS_BYPASS.remove();
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
