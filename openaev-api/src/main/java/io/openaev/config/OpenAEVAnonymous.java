package io.openaev.config;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

public class OpenAEVAnonymous implements OpenAEVPrincipal {

  public static final String ANONYMOUS = "anonymous";
  public static final String LANG_AUTO = "auto";

  @Override
  public String getId() {
    return ANONYMOUS;
  }

  @Override
  public boolean isAdmin() {
    return false;
  }

  @Override
  public String getLang() {
    return LANG_AUTO;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return null;
  }
}
