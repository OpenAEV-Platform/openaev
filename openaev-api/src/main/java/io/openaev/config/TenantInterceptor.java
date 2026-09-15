package io.openaev.config;

import static io.openaev.config.SessionHelper.ANONYMOUS_USER;

import io.openaev.aop.AccessControl;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.TenantAccessDeniedException;
import io.openaev.security.token.XtmJwksExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * Interceptor that sets the ambient {@link TenantContext} for an API request from one of two
 * sources, so the v1 mechanisms still reading it (Hibernate {@code tenantFilter}, {@code
 * TenantBaseListener} stamping, MinIO object paths, {@code findByIdAndTenantId} lookups) run under
 * the same tenant the request is scoped to instead of falling back to the default tenant.
 *
 * <ul>
 *   <li><b>Tenant-prefixed route</b> ({@code /api/tenants/{tenantId}/**}): the URL names the
 *       tenant. The authenticated user's membership is validated, then the tenant becomes the
 *       ambient one.
 *   <li><b>Regular route</b>: when there is no path tenant and {@code X-Tenant-Ids} names exactly
 *       one tenant, that id is validated exactly like a path tenant and becomes the ambient one,
 *       and the response is marked {@code Vary: X-Tenant-Ids}. Zero, blank or several ids, and
 *       anonymous callers, leave the ambient tenant untouched.
 * </ul>
 *
 * <p>The membership check is skipped for endpoints operating on the tenant resource itself (i.e.
 * annotated with {@code @AccessControl(resourceType = TENANT)}, such as tenant
 * update/delete/reactivate). On the prefixed route the {@code tenantId} is the managed target
 * rather than a scope the caller must belong to; authorization is enforced by the RBAC capability
 * check ({@code MANAGE_TENANTS}/{@code DELETE_TENANTS}) in the {@code AccessControlAspect}. On the
 * regular route such a handler addresses its target through the path, not the header, so the header
 * is not adopted at all: a create/list of tenants must not take an unvalidated ambient tenant from
 * a client-supplied header.
 *
 * <p>The regular-route header is also NOT adopted for the verified XTM One cross-platform service
 * identity ({@link XtmJwksExtractor#CROSS_PLATFORM_ATTRIBUTE}) when the handler derives its scope
 * from the parent run (a {@code @RunTenantScope} parameter): that tenant is run-authoritative,
 * derived from the {@code {runId}} by {@link OrchestratorRunTenantInterceptor}, and must never come
 * from a client-supplied header. This mirrors {@link TxCtxArgumentResolver}, which diverts the
 * service caller to the run-derived scope only on such a handler and otherwise resolves its {@code
 * TxCtx} from the header through the caller-authorized path. On the run-scoped callbacks the two
 * interceptors therefore act on disjoint callers (service identity vs non-service header caller),
 * so the ambient tenant is the same regardless of their registration order; on any other handler a
 * service caller is treated like any authenticated caller for the header, so v1 and v2 agree.
 *
 * <p>{@link AsyncHandlerInterceptor} (not just {@code HandlerInterceptor}): on an async dispatch
 * (e.g. a {@code StreamingResponseBody} endpoint) the initial servlet thread exits through {@link
 * #afterConcurrentHandlingStarted} instead of {@code afterCompletion}, and the {@link
 * TenantContext} thread-local must be cleared there too so the pooled thread does not carry the
 * previous request's tenant into an unrelated request.
 */
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements AsyncHandlerInterceptor {

  private final TenantMembershipCacheManager tenantMembershipCacheManager;
  private final TenantUriUtils tenantUriUtils;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Optional<String> pathTenant = tenantUriUtils.getTenantIdFromRequestUrl(request);
    if (pathTenant.isPresent()) {
      applyPathTenant(pathTenant.get(), handler);
    } else {
      applySingleHeaderTenant(request, response, handler);
    }
    return true;
  }

  /**
   * Tenant-prefixed route: the URL names the tenant. An authenticated caller's membership is
   * validated (except on handlers that manage the tenant resource itself), then the tenant becomes
   * the ambient one.
   */
  private void applyPathTenant(String tenantId, Object handler) {
    if (!targetsTenantResource(handler)) {
      requireMembershipForAuthenticatedCaller(tenantId);
    }
    TenantContext.setCurrentTenant(tenantId);
  }

  /**
   * Regular route: when there is no path tenant and {@code X-Tenant-Ids} names exactly one tenant,
   * adopt it as the ambient {@link TenantContext} so the v1 mechanisms still reading it (Hibernate
   * {@code tenantFilter}, {@code TenantBaseListener}, MinIO object paths, {@code
   * findByIdAndTenantId} lookups) follow the same tenant the v2 request scope does, instead of
   * running in the default tenant while the request is scoped to another. The single id is
   * validated exactly like a path tenant, with the same {@link TenantAccessDeniedException}, and
   * the response is marked {@code Vary: X-Tenant-Ids} because the header then influences it.
   *
   * <p>The header is NOT adopted, and the ambient tenant is left untouched, for: the verified
   * cross-platform service identity on a run-scoped handler (its tenant is run-authoritative, set
   * by {@link OrchestratorRunTenantInterceptor}); a handler that manages the tenant resource itself
   * (it addresses its target through the path, not the header, so a create/list must not take an
   * unvalidated ambient tenant from the header); zero, blank or several ids (a tenant-unaware
   * client sending no header keeps working, and a multi-tenant read stays a v2-only scope); and an
   * anonymous caller, mirroring {@link TxCtxArgumentResolver}, which ignores {@code X-Tenant-Ids}
   * for an anonymous caller (only a path-addressed tenant is a well-formed anonymous request).
   */
  private void applySingleHeaderTenant(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    // Skip only the run-authoritative service callback: the verified cross-platform service
    // identity on a handler whose TxCtx is derived from the parent run (@RunTenantScope), where
    // TxCtxArgumentResolver ignores the header too and OrchestratorRunTenantInterceptor sets the
    // run's tenant. On any other handler a verified service caller is treated like any
    // authenticated caller for the header, exactly as the resolver resolves its TxCtx from the
    // header through the caller-authorized path (membership-validated below).
    if (isCrossPlatformServiceCaller(request) && hasRunTenantScope(handler)) {
      return;
    }
    if (targetsTenantResource(handler)) {
      return;
    }
    Set<String> headerTenants =
        TxCtxArgumentResolver.parseTenantIdsHeader(
            request.getHeader(TxCtxArgumentResolver.TENANT_IDS_HEADER));
    if (headerTenants.size() != 1) {
      return;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || ANONYMOUS_USER.equals(authentication.getPrincipal())) {
      return;
    }
    String tenantId = headerTenants.iterator().next();
    OpenAEVPrincipal principal = (OpenAEVPrincipal) authentication.getPrincipal();
    if (!tenantMembershipCacheManager.existsByUserIdAndTenantId(principal.getId(), tenantId)) {
      throw new TenantAccessDeniedException(tenantId);
    }
    // The header influenced the ambient tenant, so the response must vary by it, the same contract
    // TxCtxArgumentResolver applies on a TxCtx parameter, extended here to a v1 handler with none.
    TxCtxArgumentResolver.markVaryByTenantHeader(response);
    TenantContext.setCurrentTenant(tenantId);
  }

  /**
   * Whether this request authenticated as the XTM One cross-platform service identity. The marker
   * is a server-side request attribute set exclusively by {@link XtmJwksExtractor} after full JWT
   * validation; a client cannot supply it.
   */
  private static boolean isCrossPlatformServiceCaller(HttpServletRequest request) {
    return Boolean.TRUE.equals(request.getAttribute(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE));
  }

  /**
   * Whether the target handler derives its tenant scope from the parent run rather than the
   * request, i.e. it has a {@code @RunTenantScope}-annotated parameter. Mirrors {@link
   * TxCtxArgumentResolver#resolveArgument}, which diverts a verified service identity to the
   * run-derived scope only on such a parameter (and only with no path tenant, always the case on
   * this regular-route branch); every other handler keeps the caller-authorized header resolution.
   */
  private static boolean hasRunTenantScope(Object handler) {
    if (handler instanceof HandlerMethod handlerMethod) {
      for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
        if (parameter.hasParameterAnnotation(RunTenantScope.class)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Validates that an authenticated, non-anonymous caller belongs to the given tenant. */
  private void requireMembershipForAuthenticatedCaller(String tenantId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && !ANONYMOUS_USER.equals(authentication.getPrincipal())) {
      OpenAEVPrincipal principal = (OpenAEVPrincipal) authentication.getPrincipal();
      if (!tenantMembershipCacheManager.existsByUserIdAndTenantId(principal.getId(), tenantId)) {
        throw new TenantAccessDeniedException(tenantId);
      }
    }
  }

  /**
   * Returns {@code true} when the target handler operates on the tenant resource itself, i.e. it is
   * annotated with {@code @AccessControl(resourceType = TENANT)}.
   */
  private boolean targetsTenantResource(Object handler) {
    if (handler instanceof HandlerMethod handlerMethod) {
      AccessControl accessControl = handlerMethod.getMethodAnnotation(AccessControl.class);
      return accessControl != null && accessControl.resourceType() == ResourceType.TENANT;
    }
    return false;
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
}
