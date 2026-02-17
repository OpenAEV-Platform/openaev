package io.openaev.config;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class DefaultOpenAEVPrincipal implements OpenAEVPrincipal, Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private final String id;
  private final List<SimpleGrantedAuthority> authorities;
  private final boolean admin;
  private final String lang;

  public DefaultOpenAEVPrincipal(
      String id, Collection<? extends GrantedAuthority> authorities, boolean admin, String lang) {
    this.id = id;
    this.authorities = new ArrayList<>();
    for (GrantedAuthority auth : authorities) {
      this.authorities.add(new SimpleGrantedAuthority(auth.getAuthority()));
    }
    this.admin = admin;
    this.lang = lang;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isAdmin() {
    return admin;
  }

  @Override
  public String getLang() {
    return lang;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }
}
