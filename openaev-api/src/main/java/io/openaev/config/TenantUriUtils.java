package io.openaev.config;

import io.openaev.utils.FilterUtilsJpa;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Optional;

@Component
public final class TenantUriUtils {

  public static final String TENANT_ID_PATH_VARIABLE = "tenantId";
  public static final String TENANT_BASE_PATH = "/api/tenants/";
  public static final String TENANT_PREFIX = TENANT_BASE_PATH + "{" + TENANT_ID_PATH_VARIABLE + "}";

  public Optional<String> getTenantIdFromRequestUrl(HttpServletRequest request) {
    Map<String, String> pathVariables =
            (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (pathVariables != null && pathVariables.containsKey(TENANT_ID_PATH_VARIABLE)) {
      return Optional.of(pathVariables.get(TENANT_ID_PATH_VARIABLE));
    }
    return Optional.empty();
  }
}
