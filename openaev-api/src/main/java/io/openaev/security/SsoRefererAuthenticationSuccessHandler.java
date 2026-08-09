package io.openaev.security;

import static org.springframework.http.HttpHeaders.REFERER;

import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionManager;
import io.openaev.database.model.EventStatus;
import io.openaev.utils.log.LogUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

public class SsoRefererAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final RequestCache requestCache = new HttpSessionRequestCache();
  private final Optional<AuditLogger> auditLogger;
  private final SessionManager sessionManager;

  public SsoRefererAuthenticationSuccessHandler(
      AuditLogger auditLogger, SessionManager sessionManager) {
    this.auditLogger = Optional.ofNullable(auditLogger);
    this.sessionManager = sessionManager;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws ServletException, IOException {

    // Audit: log SSO login success
    String provider =
        authentication instanceof OAuth2AuthenticationToken oauth2Token
            ? oauth2Token.getAuthorizedClientRegistrationId()
            : LogUtils.getAuthEventProviderSSO();

    auditLogger.ifPresent(
        logger -> {
          logger.logAuthEvent(AuditEventScope.LOGIN, EventStatus.SUCCESS, provider, null);
        });

    // Capture auth context in session for reliable expiry audit metadata and index the session
    // by user id so it can be managed (refresh, kill, concurrency limit) across restarts.
    if (authentication.getPrincipal() instanceof OpenAEVPrincipal principal) {
      SessionManager.markAuthenticatedSession(request, principal.getId());
      // Enforce the max concurrent sessions platform setting (oldest sessions are evicted).
      this.sessionManager.enforceSessionLimit(principal.getId(), request.getSession().getId());
    }

    SavedRequest savedRequest = this.requestCache.getRequest(request, response);

    if (savedRequest != null) {
      List<String> refererValues = savedRequest.getHeaderValues(REFERER);
      if (refererValues.size() == 1) {
        this.getRedirectStrategy().sendRedirect(request, response, refererValues.get(0));
        return;
      }
    }
    super.onAuthenticationSuccess(request, response, authentication);
  }
}
