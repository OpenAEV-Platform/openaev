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

  /**
   * Self-isolated tables: tables the schema derivation reads as strict but that must not be gated
   * by the statement inspector, so {@link #withoutSelfIsolated} leaves them out. "Self-isolated"
   * means the table does not rely on the inspector for its isolation, for one of the reasons given
   * per table below: it carries its own tenant predicates, it holds no tenant-scoped data at all,
   * or gating it would make the scope depend on the rows that compute it.
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
   *   <li>{@code users_tenants}: the membership mapping between a user and a tenant, platform data
   *       rather than a tenant's. Its {@code tenant_id} is half of the primary key and a foreign
   *       key onto the registry, never an attribution stamp, and the column is not nullable, so the
   *       table cannot hold a platform row either. It is derived as a tenant table only because
   *       that column is {@code NOT NULL}. Gating it is circular: the membership check that
   *       computes a request's scope runs in {@code TenantInterceptor.preHandle}, before any
   *       transaction and so before any scope exists, and {@code can_access_tenant} answers false
   *       when there is no scope, so every member would be refused every tenant-prefixed request.
   *       The same holds for token authentication and the membership reads of background threads.
   *       What stands in for the inspector is not the statements themselves, which only bind a
   *       {@code user_id} or a {@code tenant_id}: that says which row a statement targets, not
   *       whether the tenant it writes is the right one. Four things do. Every writer of the table
   *       is named, with where its tenant comes from, in {@code UsersTenantsWritersArchTest}, which
   *       fails on a writer it does not list and on a listed one that no longer writes; the request
   *       writers take the tenant of the row from the request's write scope through {@code
   *       TenantWriteScopeResolver}. The endpoints sit behind the caller's membership check on the
   *       addressed tenant. And the write-attribution detector, which keeps this table in its
   *       watch, reports in the test suite a row written outside its transaction's scope and fails
   *       a test on one its baseline does not list. See also {@code TenantMembershipGuardTest} and
   *       {@code WriteAttrMembershipTableTest}.
   * </ul>
   *
   * A table belongs here only with that kind of written reason. A table this list names has no
   * ambient isolation at all.
   */
  private static final Set<String> SELF_ISOLATED_TABLES =
      Set.of("attackpath_graph_version", "tenants", "users_tenants");

  /**
   * The self-isolated tables, exposed so tooling that must leave them out (a schema-wide sweep, a
   * detector attaching to every tenant table) derives the set from here rather than restating it.
   */
  public static Set<String> selfIsolatedTables() {
    return SELF_ISOLATED_TABLES;
  }

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
   * The tables the statement inspector gates: every strict and dual-scope table but the
   * self-isolated ones. A dual-scope table is covered because its platform rows (no tenant) are
   * written only by a transaction the server marked platform-write ({@code TenantDimension}, {@code
   * io.openaev.aop.PlatformWrite}).
   */
  public TenantTables withoutSelfIsolated() {
    Set<String> gatedStrict = new HashSet<>(strict);
    gatedStrict.removeAll(SELF_ISOLATED_TABLES);
    Set<String> gatedDualScope = new HashSet<>(dualScope);
    gatedDualScope.removeAll(SELF_ISOLATED_TABLES);
    return new TenantTables(gatedStrict, gatedDualScope);
  }
}
