package io.openaev.aop.audit_log;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.regex.Pattern;

public class AuditRequestFilter {

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
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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
