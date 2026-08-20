package io.openaev.config;

import io.openaev.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

@Component
public final class TenantUriUtils {

  public static final String TENANT_ID_PATH_VARIABLE = "tenantId";
  public static final String TENANT_BASE_PATH = "/api/tenants/";
  public static final String TENANT_PREFIX = TENANT_BASE_PATH + "{" + TENANT_ID_PATH_VARIABLE + "}";

  private static final String UUID_REGEX =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  // Tenant detection feeds security decisions (connector JWT verification), so the
  // fallback regex is strict: the tenant segment must be a full UUID and must end at a
  // path-segment boundary to avoid extracting a truncated id from a malformed URI.
  private final Pattern tenantPattern =
      Pattern.compile("^" + TENANT_BASE_PATH + "(" + UUID_REGEX + ")(?:/|$)");

  public Optional<String> getTenantIdFromRequestUrl(HttpServletRequest request) {
    Object pathVariablesAttribute =
        request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (pathVariablesAttribute instanceof Map<?, ?> pathVariables
        && pathVariables.get(TENANT_ID_PATH_VARIABLE) instanceof String tenantId) {
      return Optional.of(tenantId);
    }

    // Fallback for callers running before handler mapping populated the path variables
    // (e.g. authentication filters): parse the tenant id straight from the request URI.
    // getRequestURI() includes the servlet context path, so strip it first to keep the
    // anchored pattern working for deployments served under a non-root context path.
    String uri = request.getRequestURI();
    if (!StringUtils.isBlank(uri)) {
      String contextPath = request.getContextPath();
      if (!StringUtils.isBlank(contextPath) && uri.startsWith(contextPath)) {
        uri = uri.substring(contextPath.length());
      }
      Matcher matcher = tenantPattern.matcher(uri);
      if (matcher.find()) {
        return Optional.of(matcher.group(1));
      }
    }

    return Optional.empty();
  }
}
