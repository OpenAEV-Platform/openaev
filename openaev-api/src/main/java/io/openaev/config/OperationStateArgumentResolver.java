package io.openaev.config;

import static io.openaev.config.SessionHelper.ANONYMOUS_USER;
import static io.openaev.config.TenantUriUtils.TENANT_ID_PATH_VARIABLE;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.ExecState;
import io.openaev.rest.exception.TenantAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Spring MVC argument resolver that injects an {@link ExecState} into any controller method that
 * declares it as a parameter.
 *
 * <p>For tenant-scoped requests ({@code /api/tenants/{tenantId}/**}), extracts the tenant ID from
 * the URL path variable and validates that the authenticated user belongs to that tenant.
 *
 * <p>For non-tenant requests (e.g. admin routes), returns an {@link ExecState} with a null tenant
 * ID — callers must handle this case explicitly.
 *
 * <p>Usage in a controller:
 *
 * <pre>{@code
 * @GetMapping({"/api/documents", "/api/tenants/{tenantId}/documents"})
 * public List<RawDocument> documents(OperationState state) {
 *     return documentRepository.forCurrentTenant().rawAllDocuments(state.tenantId());
 * }
 * }</pre>
 */
@Component
@RequiredArgsConstructor
public class OperationStateArgumentResolver implements HandlerMethodArgumentResolver {

  private final TenantMembershipCacheManager tenantMembershipCacheManager;

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return ExecState.class.equals(parameter.getParameterType());
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {

    HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
    Map<String, String> pathVariables =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

    if (pathVariables == null || !pathVariables.containsKey(TENANT_ID_PATH_VARIABLE)) {
      // Non-tenant route (admin, public, etc.) — no tenant context
      return ExecState.empty();
    }

    String tenantId = pathVariables.get(TENANT_ID_PATH_VARIABLE);

    // Validate the authenticated user belongs to this tenant
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && !ANONYMOUS_USER.equals(authentication.getPrincipal())) {
      OpenAEVPrincipal principal = (OpenAEVPrincipal) authentication.getPrincipal();
      if (!tenantMembershipCacheManager.existsByUserIdAndTenantId(principal.getId(), tenantId)) {
        throw new TenantAccessDeniedException(tenantId);
      }
    }

    return ExecState.of(tenantId);
  }
}
