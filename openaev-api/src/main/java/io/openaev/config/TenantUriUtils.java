package io.openaev.config;

import io.openaev.utils.RequestUtils;
import io.openaev.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

@Component
@RequiredArgsConstructor
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

  private final RequestUtils requestUtils;

  public Optional<String> getTenantIdFromRequestUrl(HttpServletRequest request) {
    Optional<String> pathTenantId = pathTenantId(request);
    if (pathTenantId.isPresent()) {
      return pathTenantId;
    }

    // Fallback for callers running before handler mapping populated the path variables
    // (e.g. authentication filters): parse the tenant id straight from the request URI.
    // getRequestURI() includes the servlet context path, so strip it first to keep the
    // anchored pattern working for deployments served under a non-root context path.
    String uri = requestUtils.getContextStrippedUri(request);
    if (!StringUtils.isBlank(uri)) {
      Matcher matcher = tenantPattern.matcher(uri);
      if (matcher.find()) {
        return Optional.of(matcher.group(1));
      }
    }

    return Optional.empty();
  }

  /**
   * Whether a request mapped to a controller came in on a tenant-prefixed route, whose URL names
   * the tenant it reads in.
   */
  public static boolean isTenantPrefixedRoute(HttpServletRequest request) {
    return pathTenantId(request).isPresent();
  }

  /**
   * Cache policy of an image whose bytes depend on the tenant it is read in. A tenant-prefixed URL
   * names that tenant, so the image is cacheable for five minutes. A non-prefixed URL takes the
   * tenant from the caller ({@code X-Tenant-Ids} or its memberships), so callers of different
   * tenants share that URL and the image is not stored.
   */
  public static CacheControl tenantImageCacheControl(HttpServletRequest request) {
    return isTenantPrefixedRoute(request)
        ? CacheControl.maxAge(5, TimeUnit.MINUTES)
        : CacheControl.noStore();
  }

  private static Optional<String> pathTenantId(HttpServletRequest request) {
    Object pathVariablesAttribute =
        request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (pathVariablesAttribute instanceof Map<?, ?> pathVariables
        && pathVariables.get(TENANT_ID_PATH_VARIABLE) instanceof String tenantId) {
      return Optional.of(tenantId);
    }
    return Optional.empty();
  }
}
