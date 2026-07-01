package io.openaev.config;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.rest.exception.TenantSelectorRequiredException;
import io.openaev.service.tenants.TenantService;
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
 */
@Component
@RequiredArgsConstructor
public class TxCtxArgumentResolver implements HandlerMethodArgumentResolver {

  static final String TENANT_ID_PATH_VARIABLE = "tenantId";
  static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  private final TenantScopeResolver scopeResolver;
  private final TenantService tenantService;

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
    Set<String> selector = extractSelector(webRequest);
    if (selector.isEmpty() && parameter.hasParameterAnnotation(RequireTenantSelector.class)) {
      throw new TenantSelectorRequiredException();
    }
    Set<String> authorized =
        tenantService.findTenantsByUserId(SessionHelper.currentUser().getId()).stream()
            .map(Tenant::getId)
            .collect(Collectors.toSet());
    return scopeResolver.resolve(selector, authorized);
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

  @SuppressWarnings("unchecked")
  private String pathTenant(NativeWebRequest webRequest) {
    Map<String, String> pathVariables =
        (Map<String, String>)
            webRequest.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    return pathVariables == null ? null : pathVariables.get(TENANT_ID_PATH_VARIABLE);
  }
}
