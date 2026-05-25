package io.openaev.security;

import static org.springframework.http.HttpHeaders.REFERER;

import io.openaev.aop.audit_log.AccessControlAuditLogger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

public class SsoRefererAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final RequestCache requestCache = new HttpSessionRequestCache();
  private final AccessControlAuditLogger accessControlAuditLogger;

  public SsoRefererAuthenticationSuccessHandler(AccessControlAuditLogger accessControlAuditLogger) {
    this.accessControlAuditLogger = accessControlAuditLogger;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws ServletException, IOException {

    // Audit: log SSO login success
    String provider = "sso";

    try {
      if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
        provider = oauth2Token.getAuthorizedClientRegistrationId();
      }
    } catch (Exception e) {
      // Never block the login flow
    }

    accessControlAuditLogger.logAuthEvent("login", "success", provider, null, null);

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
