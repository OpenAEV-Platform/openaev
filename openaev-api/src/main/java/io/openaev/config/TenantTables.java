package io.openaev.config;

import io.openaev.database.model.DualScopeBase;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.HashSet;
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

  /**
   * Derives the tenant tables from the entity model: an entity implementing {@link DualScopeBase}
   * is a dual-scope table, one implementing {@link TenantBase} is strict, others are ignored. The
   * table name is read from the entity's {@code @Table}; a tenant-aware entity without one fails
   * fast.
   */
  public static TenantTables fromEntities(Collection<? extends Class<?>> entities) {
    Set<String> strict = new HashSet<>();
    Set<String> dualScope = new HashSet<>();
    for (Class<?> entity : entities) {
      boolean dual = DualScopeBase.class.isAssignableFrom(entity);
      boolean strictFamily = TenantBase.class.isAssignableFrom(entity);
      if (!dual && !strictFamily) {
        continue;
      }
      (dual ? dualScope : strict).add(tableName(entity));
    }
    return new TenantTables(strict, dualScope);
  }

  private static String tableName(Class<?> entity) {
    // @Table is not inherited; a SINGLE_TABLE subclass shares its parent's table, so walk up the
    // class hierarchy to find the nearest mapping.
    for (Class<?> type = entity;
        type != null && type != Object.class;
        type = type.getSuperclass()) {
      Table table = type.getAnnotation(Table.class);
      if (table != null && !table.name().isBlank()) {
        return table.name();
      }
    }
    throw new IllegalStateException(
        "tenant-aware entity must declare @Table(name=...): " + entity.getName());
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

  /** Strips the surrounding double quotes an SQL dialect may put around an identifier. */
  private static String unquote(String name) {
    if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
      return name.substring(1, name.length() - 1);
    }
    return name;
  }

  public Family family(String table) {
    String name = unquote(table).toLowerCase(Locale.ROOT);
    // Prefer STRICT on a (very unlikely) conflict: hiding platform rows is safer than exposing
    // them.
    if (strict.contains(name)) {
      return Family.STRICT;
    }
    if (dualScope.contains(name)) {
      return Family.DUAL;
    }
    return Family.NONE;
  }

  /**
   * Restricts these tables to an activation allowlist: only listed tables stay active. This is the
   * table-by-table rollout knob; an empty allowlist activates nothing, so the inspector stays
   * inert. An entry that is not a known tenant table fails fast, to surface a typo at startup
   * rather than silently leave a table unprotected.
   */
  public TenantTables restrictTo(Collection<String> allowlist) {
    Set<String> allowed = lowercase(new HashSet<>(allowlist));
    Set<String> known = new HashSet<>(strict);
    known.addAll(dualScope);
    Set<String> unknown = new HashSet<>(allowed);
    unknown.removeAll(known);
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException("active-tables are not tenant-aware tables: " + unknown);
    }
    return new TenantTables(retainOnly(strict, allowed), retainOnly(dualScope, allowed));
  }

  private static Set<String> retainOnly(Set<String> names, Set<String> allowed) {
    Set<String> kept = new HashSet<>(names);
    kept.retainAll(allowed);
    return kept;
  }
}
