package io.openaev.security;

import io.openaev.config.OpenAEVOAuth2User;
import io.openaev.config.OpenAEVOidcUser;
import io.openaev.database.model.User;
import org.springframework.security.core.Authentication;

public class SsoRefererAuthenticationUtils {

  private SsoRefererAuthenticationUtils() {}

  /** Extracts the authenticated {@link User} from the given {@link Authentication}. */
  public static User extractUser(Authentication authentication) {
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
