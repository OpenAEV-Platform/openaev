package io.openaev.config;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.rest.exception.TenantSelectorRequiredException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Resolves a {@link TxCtx} controller parameter from the request, then hands it to {@link
 * TenantScopeResolver} so the caller's rights, not the request, decide the scope. The selector is
 * taken from the URL path {@code {tenantId}} when present (it is the tenant of the resource being
 * addressed), otherwise from the {@code X-Tenant-Ids} header (one or more comma-separated ids),
 * otherwise empty (which resolves to the caller's full allowed set). The resolved {@code TxCtx} is
 * the value the transaction aspect writes into the tenant scope.
 *
 * <p>A {@link RunTenantScope}-annotated parameter is the one exception to "the caller decides": it
 * is a service-identity callback whose scope is derived from the parent autonomous run named by the
 * {@code {runId}} path variable, independent of the caller's selector or memberships (see the
 * annotation for why). The exception exists ONLY on the legacy non-prefixed route the orchestrator
 * actually rides: when the request addresses a tenant through the {@code {tenantId}} path variable
 * (the tenant-prefixed operator route), the standard caller-authorized resolution applies, so the
 * prefixed API keeps its uniform "the URL names the tenant, rights are the boundary" contract.
 */
@Component
@RequiredArgsConstructor
public class TxCtxArgumentResolver implements HandlerMethodArgumentResolver {

  static final String TENANT_ID_PATH_VARIABLE = "tenantId";
  static final String RUN_ID_PATH_VARIABLE = "runId";
  static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  private final TenantScopeResolver scopeResolver;
  private final TenantMembershipCacheManager membershipCache;
  private final AutonomousRunTenantLocator runTenantLocator;

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return TxCtx.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    if (parameter.hasParameterAnnotation(RunTenantScope.class) && !hasPathTenant(webRequest)) {
      return runTenantScope(webRequest);
    }
    Set<String> selector = extractSelector(webRequest);
    Set<String> authorized =
        new LinkedHashSet<>(
            membershipCache.findTenantIdsByUserId(SessionHelper.currentUser().getId()));
    if (selector.isEmpty() && parameter.hasParameterAnnotation(RequireTenantSelector.class)) {
      selector = fallbackSelector(authorized);
    }
    return scopeResolver.resolve(selector, authorized);
  }

  /**
   * Service-identity scope for an orchestrator callback: the run's own tenant, read scope-free by
   * its {@code {runId}} path variable, never the caller's memberships. An absent or unknown run
   * yields {@link TxCtx#missing()} (fail-closed), which the callback then surfaces as a 404 through
   * its own run lookup. Deliberately does NOT vary the response by {@code X-Tenant-Ids}: the scope
   * depends only on the run id already in the path, so the header is ignored here. Only reachable
   * on the legacy non-prefixed route: {@link #resolveArgument} keeps the tenant-prefixed route on
   * the caller-authorized resolution, so this derivation never overrides an explicitly addressed
   * tenant.
   */
  private TxCtx runTenantScope(NativeWebRequest webRequest) {
    String runId = pathVariable(webRequest, RUN_ID_PATH_VARIABLE);
    if (runId == null || runId.isBlank()) {
      return TxCtx.missing();
    }
    return runTenantLocator
        .findRunTenant(runId.trim())
        .map(TxCtx::forTenant)
        .orElse(TxCtx.missing());
  }

  /**
   * Fallback scope for single-tenant endpoints (composite-PK lookups, row attribution) when the
   * request carries no selector. An explicit selector is never mandatory: tenant-unaware API
   * clients (collectors, injectors, plain scripts) must keep working. A caller authorized on a
   * single tenant (every Community Edition deployment, single-tenant users in EE) is unambiguous
   * as-is; a multi-tenant caller falls back to the default tenant, mirroring the platform-wide
   * convention for requests without an explicit tenant context (see {@link
   * io.openaev.context.TenantContext#getCurrentTenant()}, issues #6331 / #6332). Only a
   * multi-tenant caller without access to the default tenant remains genuinely ambiguous and is
   * refused (400), since silently picking one of its tenants could read or write the wrong one.
   */
  private static Set<String> fallbackSelector(Set<String> authorized) {
    if (authorized.size() <= 1) {
      // resolve() maps an empty selector to the caller's full allowed set, which is already a
      // single-tenant (or fail-closed empty) scope here.
      return Set.of();
    }
    if (authorized.contains(Tenant.DEFAULT_TENANT_UUID)) {
      return Set.of(Tenant.DEFAULT_TENANT_UUID);
    }
    throw new TenantSelectorRequiredException();
  }

  private Set<String> extractSelector(NativeWebRequest webRequest) {
    String pathTenant = pathTenant(webRequest);
    if (pathTenant != null && !pathTenant.isBlank()) {
      return Set.of(pathTenant.trim());
    }
    // No path tenant: the X-Tenant-Ids header can influence the scope, so the response must vary by
    // it. A shared cache must never serve one tenant's response to another for the same URL.
    markVaryByTenantHeader(webRequest);
    String header = webRequest.getHeader(TENANT_IDS_HEADER);
    if (header != null && !header.isBlank()) {
      return Arrays.stream(header.split(","))
          .map(String::trim)
          .filter(id -> !id.isEmpty())
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    return Set.of();
  }

  private void markVaryByTenantHeader(NativeWebRequest webRequest) {
    HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
    if (response != null) {
      response.addHeader(HttpHeaders.VARY, TENANT_IDS_HEADER);
    }
  }

  private String pathTenant(NativeWebRequest webRequest) {
    return pathVariable(webRequest, TENANT_ID_PATH_VARIABLE);
  }

  /** Whether the request addresses a tenant through the tenant-prefixed route. */
  private boolean hasPathTenant(NativeWebRequest webRequest) {
    String pathTenant = pathTenant(webRequest);
    return pathTenant != null && !pathTenant.isBlank();
  }

  @SuppressWarnings("unchecked")
  private String pathVariable(NativeWebRequest webRequest, String key) {
    Map<String, String> pathVariables =
        (Map<String, String>)
            webRequest.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    return pathVariables == null ? null : pathVariables.get(key);
  }
}
