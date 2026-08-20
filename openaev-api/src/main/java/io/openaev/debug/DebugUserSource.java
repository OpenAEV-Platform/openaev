package io.openaev.debug;

import io.openaev.config.OpenAEVAnonymous;
import io.openaev.config.SessionHelper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The id of the user who triggered the current request, to tag debug logs with {@code user=...}.
 * Reads the security context ({@link SessionHelper}); returns {@code "anonymous"} when there is no
 * authenticated user (no token, or a token whose {@link Authentication#isAuthenticated()} is false)
 * and {@code "-"} if the context cannot be read. Never throws.
 */
public class DebugUserSource {

  static final String UNKNOWN = "-";

  public String currentUser() {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication == null || !authentication.isAuthenticated()) {
        // An unauthenticated token must not be attributed as a user.
        return OpenAEVAnonymous.ANONYMOUS;
      }
      String id = SessionHelper.currentUser().getId();
      return (id == null || id.isBlank()) ? UNKNOWN : id;
    } catch (RuntimeException e) {
      return UNKNOWN;
    }
  }
}
