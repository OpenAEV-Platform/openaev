package io.openaev.config;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

public interface OpenAEVPrincipal extends Serializable {
  String getId();

  Collection<? extends GrantedAuthority> getAuthorities();

  boolean isAdmin();

  String getLang();

  List<String> tenantIds();
}
