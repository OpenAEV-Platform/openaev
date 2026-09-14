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

  /** Activation allowlist entry meaning "every strict table"; see {@link #restrictTo}. */
  public static final String ALL_STRICT = "*";

  /**
   * Strict tables deliberately and permanently outside v2, so {@link #ALL_STRICT} leaves them
   * alone. Each one isolates itself, and activating it would break the mechanism it isolates itself
   * with.
   *
   * <ul>
   *   <li>{@code attackpath_graph_version}: its version counter is bumped by a single native {@code
   *       INSERT ... ON CONFLICT DO UPDATE ... RETURNING}, a shape the inspector cannot rewrite.
   *       Every statement on that table carries its own tenant predicate instead and the counter is
   *       keyed by {@code (simulation_id, tenant_id)}; see {@code
   *       AttackPathGraphVersionRepository}.
   *   <li>{@code tenants}: the registry of tenants, not tenant-scoped data. It is derived as a
   *       tenant table only because its primary key column happens to be named {@code tenant_id},
   *       which the schema derivation reads as "this row belongs to a tenant". Gating it stops the
   *       platform from starting: the bootstrap must read the default tenant in order to create it,
   *       and under an empty scope that read returns nothing, so the admin user's save fails
   *       resolving the tenant. Which tenants a caller may see is a permission question, answered
   *       by group membership (see #7248), not a row-filtering one; two mechanisms answering it
   *       would eventually disagree.
   * </ul>
   *
   * A table belongs here only with that kind of written reason. "Not activated yet" is not one.
   */
  private static final Set<String> OUTSIDE_V2 = Set.of("attackpath_graph_version", "tenants");

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
   *
   * <p>The single entry {@value #ALL_STRICT} activates every strict table except those listed in
   * {@code OUTSIDE_V2}, and no dual-scope one: the rollout's terminal state, and what the nightly
   * shadow run passes to surface what activating everything would break. Dual-scope tables stay out
   * because a platform row is written with no tenant, which this mechanism does not cover. It is
   * rejected alongside other entries, so a list always reads as either "these tables" or "all of
   * them", never both.
   */
  public TenantTables restrictTo(Collection<String> allowlist) {
    Set<String> allowed = lowercase(new HashSet<>(allowlist));
    if (allowed.contains(ALL_STRICT)) {
      if (allowed.size() > 1) {
        throw new IllegalArgumentException(
            "active-tables is either '"
                + ALL_STRICT
                + "' or a list of tables, not both: "
                + allowed);
      }
      Set<String> activable = new HashSet<>(strict);
      activable.removeAll(OUTSIDE_V2);
      return new TenantTables(activable, Set.of());
    }
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
