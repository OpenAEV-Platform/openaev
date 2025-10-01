package io.openaev.config;

import static io.openaev.database.model.User.ROLE_ADMIN;
import static io.openaev.database.model.User.ROLE_USER;

import io.openaev.database.model.User;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class OpenAEVOAuth2User implements OpenAEVPrincipal, OAuth2User {

  private final User user;

  public OpenAEVOAuth2User(@NotNull final User user) {
    this.user = user;
  }

  @Override
  public Map<String, Object> getAttributes() {
    HashMap<String, Object> attributes = new HashMap<>();
    attributes.put("id", this.user.getId());
    attributes.put("name", this.user.getFirstname() + " " + this.user.getLastname());
    attributes.put("email", this.user.getEmail());
    return attributes;
  }

  @Override
  public String getId() {
    return this.user.getId();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    List<SimpleGrantedAuthority> roles = new ArrayList<>();
    roles.add(new SimpleGrantedAuthority(ROLE_USER));
    if (this.user.isAdmin()) {
      roles.add(new SimpleGrantedAuthority(ROLE_ADMIN));
    }
    return roles;
  }

  @Override
  public boolean isAdmin() {
    return false;
  }

  @Override
  public String getLang() {
    return this.user.getLang();
  }

  @Override
  public String getName() {
    return this.user.getFirstname() + " " + this.user.getLastname();
  }
}
