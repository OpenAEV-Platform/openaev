package io.openaev.config;

import static io.openaev.config.SessionHelper.ANONYMOUS_USER;

import io.openaev.aop.AccessControl;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.TenantAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * Interceptor that automatically extracts the {@code tenantId} path variable from any request
 * matching {@code /api/tenants/{tenantId}/**}, validates the authenticated user belongs to that
 * tenant, and sets it in the {@link TenantContext}.
 *
 * <p>The membership check is skipped for endpoints operating on the tenant resource itself (i.e.
 * annotated with {@code @AccessControl(resourceType = TENANT)}, such as tenant
 * update/delete/reactivate). For those, the {@code tenantId} is the managed target rather than a
 * scope the caller must belong to; authorization is enforced by the RBAC capability check ({@code
 * MANAGE_TENANTS}/{@code DELETE_TENANTS}) in the {@code AccessControlAspect}.
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
      applySingleHeaderTenant(request, handler);
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
   * validated exactly like a path tenant, with the same {@link TenantAccessDeniedException} and the
   * same exemption for handlers that manage the tenant resource.
   *
   * <p>Zero, blank or several ids leave the ambient tenant untouched (the default): a
   * tenant-unaware client sending no header keeps working, and a multi-tenant read of several ids
   * stays a v2-only scope. An anonymous caller is left untouched too, mirroring {@link
   * TxCtxArgumentResolver}, which ignores {@code X-Tenant-Ids} for an anonymous caller (only a
   * path-addressed tenant is a well-formed anonymous request).
   */
  private void applySingleHeaderTenant(HttpServletRequest request, Object handler) {
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
    if (!targetsTenantResource(handler)) {
      OpenAEVPrincipal principal = (OpenAEVPrincipal) authentication.getPrincipal();
      if (!tenantMembershipCacheManager.existsByUserIdAndTenantId(principal.getId(), tenantId)) {
        throw new TenantAccessDeniedException(tenantId);
      }
    }
    TenantContext.setCurrentTenant(tenantId);
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
