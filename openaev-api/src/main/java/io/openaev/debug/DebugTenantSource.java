package io.openaev.debug;

import io.openaev.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Tenant to tag a request's logs with, under both tenant mechanisms. There is no ambient accessor
 * for the v2 {@code TxCtx} (it is passed as a method argument by design), so this reads the v2
 * request selector - same inputs as {@code TxCtxArgumentResolver} - and falls back to the v1 {@link
 * TenantContext}. Resolution lives here only, so a future switch is a one-class change.
 */
public class DebugTenantSource {

  // Mirror TxCtxArgumentResolver (its constants are package-private there).
  static final String TENANT_ID_PATH_VARIABLE = "tenantId";
  static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  // The selector is caller-controlled (header / URL path), so it is sanitised before the MDC: keep
  // only tenant-id chars (drops newlines that could forge log lines), cap the length.
  private static final Pattern TENANT_CHARS = Pattern.compile("[^A-Za-z0-9,_-]");
  private static final int MAX_TENANT_TAG_LENGTH = 128;

  public String currentTenant(HttpServletRequest request) {
    String selector = requestSelector(request);
    return selector != null ? selector : TenantContext.getCurrentTenant();
  }

  private String requestSelector(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (vars instanceof Map<?, ?> pathVariables
        && pathVariables.get(TENANT_ID_PATH_VARIABLE) instanceof String pathTenant) {
      String safe = sanitize(pathTenant);
      if (safe != null) {
        return safe;
      }
    }
    return sanitize(request.getHeader(TENANT_IDS_HEADER));
  }

  /** Keeps tenant-id chars, caps length; {@code null} when nothing is left. */
  private static String sanitize(String raw) {
    if (raw == null) {
      return null;
    }
    String cleaned = TENANT_CHARS.matcher(raw).replaceAll("");
    if (cleaned.isEmpty()) {
      return null;
    }
    return cleaned.length() > MAX_TENANT_TAG_LENGTH
        ? cleaned.substring(0, MAX_TENANT_TAG_LENGTH)
        : cleaned;
  }
}
