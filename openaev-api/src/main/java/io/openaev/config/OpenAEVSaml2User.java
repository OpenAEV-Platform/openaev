package io.openaev.config;

import io.openaev.database.model.User;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;

/**
 * Lightweight, fully serializable SAML2 principal.
 *
 * <p>Only scalar snapshots of the user are stored (never the JPA {@link User} entity itself) so the
 * security context can be serialized into the PostgreSQL-backed session store (Spring Session JDBC)
 * and survive platform restarts.
 */
public class OpenAEVSaml2User
    implements OpenAEVPrincipal, Saml2AuthenticatedPrincipal, Serializable {

  @Serial private static final long serialVersionUID = 2L;

  private final String id;
  private final String name;
  private final boolean admin;
  private final String lang;
  private final List<SimpleGrantedAuthority> roles;

  public OpenAEVSaml2User(
      @NotNull final User user, @NotNull final List<SimpleGrantedAuthority> roles) {
    this.id = user.getId();
    this.name = user.getName();
    this.admin = user.isAdmin();
    this.lang = user.getLang();
    this.roles = new ArrayList<>(roles);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles;
  }

  @Override
  public boolean isAdmin() {
    return admin;
  }

  @Override
  public String getLang() {
    return lang;
  }
}
