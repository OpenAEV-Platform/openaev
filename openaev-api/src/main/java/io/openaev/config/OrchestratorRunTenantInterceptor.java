package io.openaev.config;

import io.openaev.context.TenantContext;
import io.openaev.security.token.XtmJwksExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Establishes the legacy v1 {@link TenantContext} for the orchestrator CALLBACK route, so the
 * entities those callbacks still reach through the v1 Hibernate {@code tenantFilter} (workflows,
 * injects, exercises, teams, assets, findings - scoped by {@link
 * io.openaev.aop.HibernateFilterTransactionAspect}, which reads this ThreadLocal) are filtered to
 * the run's own tenant. It is the v1 counterpart of what {@link
 * TxCtxArgumentResolver#runTenantScope} does for the v2 {@code app.current_tenants} GUC.
 *
 * <p>Why an interceptor and not per-method: the callbacks ride the legacy NON-prefixed route
 * ({@code /api/autonomous-runs/**}), which {@link TenantInterceptor} (registered only for {@code
 * /api/tenants/**}) never covers, so on that route the v1 ThreadLocal is unset and {@code
 * HibernateFilterTransactionAspect} falls back to {@link
 * io.openaev.database.model.Tenant#DEFAULT_TENANT_UUID}. A run owned by a non-default tenant would
 * then record its v2 state correctly (its {@code TxCtx} is run-derived) yet silently read/write
 * NOTHING through the v1 filter: an empty attack-path state read (so the orchestrator re-authors
 * duplicates), no workflow scope mirror, no team enablement, dropped finding-to-asset promotions.
 * The v1 filter is enabled at transaction START, so the scope has to be set BEFORE the handler's
 * {@code @Transactional} opens - {@code preHandle} is that point, and it also covers the {@code
 * REQUIRES_NEW} sessions the service opens on the same thread.
 *
 * <p>The gating mirrors {@link TxCtxArgumentResolver} EXACTLY so v1 and v2 can never derive
 * different tenants for the same request: only the VERIFIED XTM One cross-platform service identity
 * ({@link XtmJwksExtractor#CROSS_PLATFORM_ATTRIBUTE}, a server-side attribute a client cannot
 * forge), only on the non-prefixed route (no {@code {tenantId}} path variable - the prefixed
 * operator route is already scoped by {@link TenantInterceptor}), and only when a {@code {runId}}
 * names the run. Every other caller is left with the default v1 scope exactly as today, so knowing
 * a run id never turns into cross-tenant v1 reach; the tenant is read from the run's own immutable
 * {@code tenant_id} by primary key ({@link AutonomousRunTenantLocator}), and an unknown or
 * soft-deleted run resolves empty and sets nothing (fail-closed), matching the v2 derivation.
 *
 * <p>{@link AsyncHandlerInterceptor}: the ThreadLocal is cleared in BOTH {@code afterCompletion}
 * and {@code afterConcurrentHandlingStarted} - the latter is the exit path for an async dispatch
 * (e.g. a streaming callback) - so a pooled thread never carries the run's tenant into an unrelated
 * request, the same lifecycle {@link TenantInterceptor} uses. Clearing is unconditional and safe:
 * nothing else sets the v1 ThreadLocal on the non-prefixed route this interceptor is registered
 * for.
 */
@Component
@RequiredArgsConstructor
public class OrchestratorRunTenantInterceptor implements AsyncHandlerInterceptor {

  private final AutonomousRunTenantLocator runTenantLocator;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!isCrossPlatformServiceCaller(request)) {
      return true;
    }
    Map<String, String> pathVariables = pathVariables(request);
    if (pathVariables == null) {
      return true;
    }
    // The prefixed operator route names its tenant in the URL and is scoped by TenantInterceptor;
    // this interceptor only ever acts on the non-prefixed callback route.
    if (hasText(pathVariables.get(TxCtxArgumentResolver.TENANT_ID_PATH_VARIABLE))) {
      return true;
    }
    String runId = pathVariables.get(TxCtxArgumentResolver.RUN_ID_PATH_VARIABLE);
    if (!hasText(runId)) {
      return true;
    }
    runTenantLocator.findRunTenant(runId.trim()).ifPresent(TenantContext::setCurrentTenant);
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    TenantContext.clearCurrentTenant();
  }

  @Override
  public void afterConcurrentHandlingStarted(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    TenantContext.clearCurrentTenant();
  }

  /**
   * Whether this request authenticated as the XTM One cross-platform service identity. The marker
   * is a server-side request attribute set exclusively by {@link XtmJwksExtractor} after full JWT
   * validation (trusted issuer, JWKS signature, expected audience); it cannot be supplied by a
   * client.
   */
  private static boolean isCrossPlatformServiceCaller(HttpServletRequest request) {
    return Boolean.TRUE.equals(request.getAttribute(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> pathVariables(HttpServletRequest request) {
    return (Map<String, String>)
        request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
