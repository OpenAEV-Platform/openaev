package io.openaev.config;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

public class OpenAEVAnonymous implements OpenAEVPrincipal, Serializable {

  @Serial private static final long serialVersionUID = 1L;

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
  public List<String> tenantIds() {
    return List.of();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return null;
  }
}
