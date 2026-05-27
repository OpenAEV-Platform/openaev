package io.openaev.aop.audit_log;

import io.openaev.database.model.Action;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@ConditionalOnProperty(name = "openaev.audit-logs.service.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AuditRequestValidator {

  /**
   * Requests from automated clients are excluded from audit logging. Matches User-Agent headers
   * like {@code openaev-agent/2.3.0} (endpoint agents doing heartbeats/job polling) and {@code
   * pyoaev/2.2.1} (Python client used by collectors such as Atomic Red Team, AWS Resources, etc.).
   */
  private static final Pattern AGENT_USER_AGENT_PATTERN =
      Pattern.compile("^(openaev-agent|pyoaev)/", Pattern.CASE_INSENSITIVE);

  /**
   * Request URI prefixes for machine-to-machine endpoints that should be excluded from audit
   * logging. XTM Composer calls (health checks, status updates, log pushes) happen frequently and
   * are not user-initiated actions.
   */
  private static final String XTM_COMPOSER_URI_PREFIX = "/api/xtm-composer";

  public boolean valid(Action action) {
    // Skip actions we don't audit
    if (shouldSkip(action)) {
      return false;
    }

    // Skip automated requests — not user-initiated actions
    return !isAutomatedRequest();
  }

  /**
   * Unauthorized RBAC-denial events must always be auditable, even when read logging is disabled.
   */
  public boolean validUnauthorized() {
    return !isAutomatedRequest();
  }

  private boolean shouldSkip(Action action) {
    return switch (action) {
      case CREATE, WRITE, DELETE, LAUNCH, DUPLICATE -> false;
      // READ/SEARCH are never audited on success — only unauthorized attempts are logged
      // (captured separately via logAuthEvent when RBAC denies access).
      case READ, SEARCH -> true;
      default -> true; // SKIP_RBAC, PROCESS
    };
  }

  /**
   * Returns {@code true} if the current request originates from an automated client. This covers:
   *
   * <ul>
   *   <li>OpenAEV endpoint agents ({@code openaev-agent/...}) — heartbeats, job polling
   *   <li>Python client ({@code pyoaev/...}) — collectors (Atomic Red Team, AWS Resources, etc.)
   *   <li>XTM Composer callbacks — health checks, status updates, log pushes
   * </ul>
   *
   * These automated calls happen frequently and would flood the audit log with noise.
   */
  public static boolean isAutomatedRequest() {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs == null) {
        return false;
      }
      HttpServletRequest request = attrs.getRequest();

      // Check User-Agent for known automated clients
      String userAgent = request.getHeader("User-Agent");
      if (userAgent != null && AGENT_USER_AGENT_PATTERN.matcher(userAgent).find()) {
        return true;
      }

      // Check request URI for machine-to-machine endpoints
      String requestUri = request.getRequestURI();
      return requestUri != null && requestUri.startsWith(XTM_COMPOSER_URI_PREFIX);
    } catch (Exception e) {
      return false;
    }
  }
}
