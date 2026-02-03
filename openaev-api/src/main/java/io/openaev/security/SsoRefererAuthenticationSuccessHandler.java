package io.openaev.security;

import static org.springframework.http.HttpHeaders.REFERER;

import io.openaev.config.OpenAEVOAuth2User;
import io.openaev.config.OpenAEVOidcUser;
import io.openaev.database.model.User;
import io.openaev.service.user_events.UserEventService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

public class SsoRefererAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final RequestCache requestCache = new HttpSessionRequestCache();
  private final UserEventService userEventService;

  public SsoRefererAuthenticationSuccessHandler(UserEventService userEventService) {
    this.userEventService = userEventService;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws ServletException, IOException {
    User user = extractUser(authentication);
    userEventService.createLoginEvent(user);

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

  private User extractUser(Authentication authentication) {
    Object principal = authentication.getPrincipal();

    if (principal instanceof OpenAEVOidcUser oidcUser) {
      return oidcUser.getUser();
    }
    if (principal instanceof OpenAEVOAuth2User oauth2User) {
      return oauth2User.getUser();
    }
    return null;
  }
}
