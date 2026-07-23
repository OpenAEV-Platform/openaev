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
    userEventService.createLoginFailedEvent(
        request.getRequestURI(), exception.getClass().getSimpleName());

    auditLogger.ifPresent(
        logger -> {
          logger.logAuthEvent(
              AuditEventScope.LOGIN,
              EventStatus.ERROR,
              request
                  .getRequestURI(), // TODO This represents a security issue bc we can have malicius
              // log injection issues. Before log in, we should normalize and
              // sanitize this data.
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
}
