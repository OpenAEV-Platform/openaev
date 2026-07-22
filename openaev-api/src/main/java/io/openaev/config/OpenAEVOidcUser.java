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
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Lightweight, fully serializable OIDC principal.
 *
 * <p>Only scalar snapshots of the user are stored (never the JPA {@link User} entity itself) so the
 * security context can be serialized into the PostgreSQL-backed session store (Spring Session JDBC)
 * and survive platform restarts.
 */
public class OpenAEVOidcUser implements OpenAEVPrincipal, OidcUser, Serializable {
  @Serial private static final long serialVersionUID = 2L;

  private final String id;
  private final String name;
  private final String email;
  private final boolean admin;
  private final String lang;
  private final List<SimpleGrantedAuthority> authorities;

  public OpenAEVOidcUser(@NotNull final User user) {
    this.id = user.getId();
    this.name = user.getFirstname() + " " + user.getLastname();
    this.email = user.getEmail();
    this.admin = user.isAdmin();
    this.lang = user.getLang();
    List<SimpleGrantedAuthority> roles = new ArrayList<>();
    roles.add(new SimpleGrantedAuthority(ROLE_USER));
    if (user.isAdmin()) {
      roles.add(new SimpleGrantedAuthority(ROLE_ADMIN));
    }
    this.authorities = roles;
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
  public Map<String, Object> getClaims() {
    return getAttributes();
  }

  @Override
  public OidcUserInfo getUserInfo() {
    return OidcUserInfo.builder().name(name).email(email).build();
  }

  @Override
  public OidcIdToken getIdToken() {
    return null;
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
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getName() {
    return name;
  }
}
