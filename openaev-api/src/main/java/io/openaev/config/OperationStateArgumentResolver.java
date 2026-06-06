package io.openaev.config;

import io.openaev.context.ExecState;
import io.openaev.context.StateExecutionContext;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Spring MVC argument resolver that injects the current {@link ExecState} into any controller
 * method that declares it as a parameter.
 *
 * <p>The {@link ExecState} is resolved by the {@link TenantInterceptor} (which runs before argument
 * resolvers) and stored in the {@link StateExecutionContext}. This resolver simply retrieves it. If
 * no tenant context was set (e.g. admin routes without a {@code {tenantId}} path variable), an empty
 * {@link ExecState} is returned.
 *
 * <p>Usage in a controller:
 *
 * <pre>{@code
 * @GetMapping("/api/tenants/{tenantId}/documents")
 * public List<Document> documents(ExecState state) {
 *     // state.tenant()              → resolved Tenant entity
 *     // state.accessibleTenantIds() → IDs for SQL filtering
 *     newEntity.setTenant(state.tenant());
 * }
 * }</pre>
 */
@Component
public class OperationStateArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return ExecState.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {

    // TenantInterceptor already resolved the Tenant entity and set the ExecState
    ExecState state = StateExecutionContext.get();
    return state;
  }
}
