package io.openaev.config;

import static io.openaev.database.model.User.ROLE_ADMIN;
import static io.openaev.database.model.User.ROLE_USER;

import io.openaev.database.model.User;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Lightweight, fully serializable OAuth2 principal.
 *
 * <p>Only scalar snapshots of the user are stored (never the JPA {@link User} entity itself) so the
 * security context can be serialized into the PostgreSQL-backed session store (Spring Session JDBC)
 * and survive platform restarts.
 */
public class OpenAEVOAuth2User implements OpenAEVPrincipal, OAuth2User, Serializable {

  @Serial private static final long serialVersionUID = 2L;

  private final String id;
  private final String name;
  private final String email;
  private final String lang;
  private final List<SimpleGrantedAuthority> authorities;

  public OpenAEVOAuth2User(@NotNull final User user) {
    this.id = user.getId();
    this.name = user.getFirstname() + " " + user.getLastname();
    this.email = user.getEmail();
    this.lang = user.getLang();
    List<SimpleGrantedAuthority> roles = new ArrayList<>();
    roles.add(new SimpleGrantedAuthority(ROLE_USER));
    if (user.isAdmin()) {
      roles.add(new SimpleGrantedAuthority(ROLE_ADMIN));
    }
    this.authorities = roles;
  }

  @Override
  public Map<String, Object> getAttributes() {
    HashMap<String, Object> attributes = new HashMap<>();
    attributes.put("id", id);
    attributes.put("name", name);
    attributes.put("email", email);
    return attributes;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public boolean isAdmin() {
    // Historical behavior preserved: plain OAuth2 (non-OIDC) principals never expose the
    // admin flag directly; admin capabilities flow through the granted authorities.
    return false;
  }

  @Override
  public String getLang() {
    return lang;
  }

  @Override
  public String getName() {
    return name;
  }
}
