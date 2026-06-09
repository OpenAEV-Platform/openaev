package io.openaev.aop;

import io.openaev.context.TxCtx;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * AOP aspect that injects the tenant context into the PostgreSQL session before each transactional
 * method executes.
 *
 * <p>Tenant resolution order:
 *
 * <ol>
 *   <li>Explicit {@link TxCtx} method parameter (services, crons, async jobs)
 *   <li>{@code {tenantId}} path variable from the URL
 *   <li>{@code tenantIds} query parameter ({@code ?tenantIds=aaa,bbb})
 *   <li>{@code X-Tenant-Ids} HTTP header ({@code X-Tenant-Ids: aaa,bbb})
 * </ol>
 *
 * <p>Sources 2–4 are automatically activated when the method carries a Spring MVC mapping
 * annotation ({@code @GetMapping}, {@code @PostMapping}, etc.).
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TxCtxTransactionAspect {

  private static final String TENANT_ID_PATH_VARIABLE = "tenantId";
  private static final String TENANT_IDS_QUERY_PARAM = "tenantIds";
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  private static final Set<Class<? extends Annotation>> HTTP_MAPPING_ANNOTATIONS =
      Set.of(
          GetMapping.class,
          PostMapping.class,
          PutMapping.class,
          DeleteMapping.class,
          PatchMapping.class);

  @PersistenceContext private EntityManager entityManager;

  @Around(
      "@annotation(org.springframework.transaction.annotation.Transactional) || "
          + "@annotation(jakarta.transaction.Transactional)")
  public Object injectTenantContext(ProceedingJoinPoint pjp) throws Throwable {

    List<String> tenants = resolveTenants(pjp);
    if (!tenants.isEmpty()) {
      String joinedTenants = String.join(",", tenants);
      entityManager
          .createNativeQuery("SELECT set_config('app.current_tenants', :tenantIds, true)")
          .setParameter("tenantIds", joinedTenants)
          .getSingleResult();
    }

    return pjp.proceed();
  }

  // ---------------------------------------------------------------------------
  // Tenant resolution chain
  // ---------------------------------------------------------------------------

  private List<String> resolveTenants(ProceedingJoinPoint pjp) {
    // 1. Explicit TxCtx parameter (highest priority)
    TxCtx ctx = findCtx(pjp.getArgs());
    if (ctx != null && !ctx.tenantIds().isEmpty()) {
      return ctx.tenantIds();
    }

    // 2–4. From HTTP request (only for REST endpoints)
    if (!isHttpEndpoint(pjp)) {
      return List.of();
    }

    HttpServletRequest request = getCurrentRequest();
    if (request == null) {
      return List.of();
    }

    // 2. Path variable: /api/tenants/{tenantId}/...
    String pathTenant = getPathVariable(request);
    if (pathTenant != null && !pathTenant.isBlank()) {
      return List.of(pathTenant);
    }

    // 3. Query param: ?tenantIds=aaa,bbb
    String queryTenants = request.getParameter(TENANT_IDS_QUERY_PARAM);
    if (queryTenants != null && !queryTenants.isBlank()) {
      return Arrays.asList(queryTenants.split(","));
    }

    // 4. Header: X-Tenant-Ids: aaa,bbb
    String headerTenants = request.getHeader(TENANT_IDS_HEADER);
    if (headerTenants != null && !headerTenants.isBlank()) {
      return Arrays.asList(headerTenants.split(","));
    }

    return List.of();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private boolean isHttpEndpoint(ProceedingJoinPoint pjp) {
    try {
      MethodSignature signature = (MethodSignature) pjp.getSignature();
      var method = signature.getMethod();
      for (Class<? extends Annotation> mapping : HTTP_MAPPING_ANNOTATIONS) {
        if (method.isAnnotationPresent(mapping)) {
          return true;
        }
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  private HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attrs != null ? attrs.getRequest() : null;
  }

  @SuppressWarnings("unchecked")
  private String getPathVariable(HttpServletRequest request) {
    Map<String, String> pathVars =
        (Map<String, String>)
            request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    return pathVars != null ? pathVars.get(TENANT_ID_PATH_VARIABLE) : null;
  }

  private TxCtx findCtx(Object[] args) {
    if (args == null) return null;
    for (Object arg : args) {
      if (arg instanceof TxCtx context) {
        return context;
      }
    }
    return null;
  }
}
