package io.openaev.security;

import static org.springframework.http.HttpHeaders.REFERER;

import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.database.model.EventStatus;
import io.openaev.service.user_events.UserEventService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

public class SsoRefererAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

  private RequestCache requestCache = new HttpSessionRequestCache();
  private final UserEventService userEventService;
  private final Optional<AuditLogger> auditLogger;

  public SsoRefererAuthenticationFailureHandler(
      UserEventService userEventService, AuditLogger auditLogger) {
    this.userEventService = userEventService;
    this.auditLogger = Optional.ofNullable(auditLogger);
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws ServletException, IOException {
    String provider = resolveOAuth2Provider(request);

    userEventService.createLoginFailedEvent(provider, exception.getClass().getSimpleName());

    auditLogger.ifPresent(
        logger -> {
          logger.logAuthEvent(
              AuditEventScope.LOGIN,
              EventStatus.ERROR,
              provider,
              exception.getClass().getSimpleName());
        });

    this.saveException(request, exception);
    SavedRequest savedRequest = this.requestCache.getRequest(request, response);
    if (savedRequest != null) {
      List<String> refererValues = savedRequest.getHeaderValues(REFERER);
      if (refererValues.size() == 1) {
        this.getRedirectStrategy()
            .sendRedirect(
                request, response, refererValues.get(0) + "?error=" + exception.getMessage());
        return;
      }
    }
    super.onAuthenticationFailure(request, response, exception);
  }

  public void setRequestCache(RequestCache requestCache) {
    this.requestCache = requestCache;
  }

  /**
   * Extracts the OAuth2 client registration ID from the request URI. Spring Security OAuth2 login
   * callbacks follow the pattern {@code /login/oauth2/code/{registrationId}}, so the last path
   * segment is the provider name. Falls back to {@code "sso"} if extraction fails.
   */
  private static String resolveOAuth2Provider(HttpServletRequest request) {
    try {
      String uri = request.getRequestURI();
      if (uri != null) {
        int lastSlash = uri.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < uri.length() - 1) {
          return uri.substring(lastSlash + 1);
        }
      }
    } catch (Exception ignored) {
      // Fall through to default
    }
    return "sso";
  }
}
