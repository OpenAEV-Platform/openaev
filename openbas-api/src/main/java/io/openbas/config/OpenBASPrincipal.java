package io.openbas.config;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

public interface OpenBASPrincipal {

  String getId();

  Collection<? extends GrantedAuthority> getAuthorities();

  boolean isAdmin();

  String getLang();
}
