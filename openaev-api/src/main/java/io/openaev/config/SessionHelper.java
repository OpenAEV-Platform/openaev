package io.openaev.config;

import org.springframework.security.core.context.SecurityContextHolder;

public class SessionHelper {

  private SessionHelper() {}

  public static final String ANONYMOUS_USER = "anonymousUser";

  public static OpenAEVPrincipal currentUser() {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      return new OpenAEVAnonymous();
    }
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (ANONYMOUS_USER.equals(principal)) {
      return new OpenAEVAnonymous();
    }
    return (OpenAEVPrincipal) principal;
  }
}
