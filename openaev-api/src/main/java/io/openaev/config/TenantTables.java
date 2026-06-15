package io.openaev.config;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tables that carry a tenant scope, split by family: {@code strict} tables always belong to a
 * tenant, {@code dualScope} tables may also hold platform rows ({@code tenant_id IS NULL}). Names
 * are matched case-insensitively.
 */
public record TenantTables(Set<String> strict, Set<String> dualScope) {

  public enum Family {
    NONE,
    STRICT,
    DUAL
  }

  public TenantTables {
    strict = lowercase(strict);
    dualScope = lowercase(dualScope);
  }

  private static Set<String> lowercase(Set<String> names) {
    return names.stream()
        .map(name -> name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  public Family family(String table) {
    String name = table.toLowerCase(Locale.ROOT);
    if (dualScope.contains(name)) {
      return Family.DUAL;
    }
    if (strict.contains(name)) {
      return Family.STRICT;
    }
    return Family.NONE;
  }
}
