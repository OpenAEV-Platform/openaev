package io.openbas.config;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

public interface OpenBASPrincipal {

  String getId();

  Collection<? extends GrantedAuthority> getAuthorities();

  /**
   * @deprecated since 1.19.0, forRemoval = false
   *     <p>This should not be used anymore. Instead, prefer using
   *     userService.currentUser().isAdminOrByPass()
   */
  @Deprecated(since = "1.19.0")
  boolean isAdmin();

  String getLang();
}
