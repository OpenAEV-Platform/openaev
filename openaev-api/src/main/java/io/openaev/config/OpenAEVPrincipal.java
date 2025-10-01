package io.openaev.config;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

public interface OpenAEVPrincipal {

  String getId();

  Collection<? extends GrantedAuthority> getAuthorities();

  boolean isAdmin();

  String getLang();
}
