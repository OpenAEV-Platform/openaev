package io.openaev.security;

import static org.springframework.http.HttpHeaders.REFERER;

import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.database.model.Action;
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

  public SsoRefererAuthenticationSuccessHandler(AuditLogger auditLogger) {
    this.auditLogger = Optional.ofNullable(auditLogger);
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
          String eventScope = LogUtils.getEventScope(Action.LOGIN);
          String eventStatus = LogUtils.getEventStatus(EventStatus.SUCCESS);
          logger.logAuthEvent(eventScope, eventStatus, provider, null, null);
        });

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
