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
@SuppressWarnings("unchecked")
public final class TenantUriUtils {

  public static final String TENANT_ID_PATH_VARIABLE = "tenantId";
  public static final String TENANT_BASE_PATH = "/api/tenants/";
  public static final String TENANT_PREFIX = TENANT_BASE_PATH + "{" + TENANT_ID_PATH_VARIABLE + "}";
  private final Pattern tenantPattern =
      Pattern.compile("^" + TENANT_BASE_PATH + "([A-Fa-f0-9-]+)/?");

  public Optional<String> getTenantIdFromRequestUrl(HttpServletRequest request) {
    Map<String, String> pathVariables =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (pathVariables != null && pathVariables.containsKey(TENANT_ID_PATH_VARIABLE)) {
      return Optional.of(pathVariables.get(TENANT_ID_PATH_VARIABLE));
    }

    // second attempt
    String uri = request.getRequestURI();
    if (!StringUtils.isBlank(uri)) {
      Matcher matcher = tenantPattern.matcher(request.getRequestURI());
      if (matcher.find()) {
        return Optional.of(matcher.group(1));
      }
    }

    return Optional.empty();
  }
}
