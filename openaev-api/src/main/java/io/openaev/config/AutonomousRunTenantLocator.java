package io.openaev.config;

import io.openaev.annotation.AllowRawJdbc;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads an autonomous run's own {@code tenant_id} by primary key, scope-free, so {@link
 * RunTenantScope} can derive the service-identity scope of an orchestrator callback from the run
 * itself.
 *
 * <p>Raw JDBC on purpose: this lookup is what SETS the transaction's tenant scope, so it cannot
 * itself run under that scope without a chicken-and-egg - a scoped read of {@code autonomous_runs}
 * (a tenant-active table) would be filtered by {@code can_access_tenant} against a scope that has
 * not been decided yet, and would return nothing for exactly the callers this feature must serve.
 * It therefore has to bypass the Hibernate statement inspector. No tenant guarantee is lost: it
 * reads only the run's own immutable {@code tenant_id} by its primary key, plus the owning tenant's
 * liveness flag, and returns it for the aspect to enforce for the rest of the transaction; it never
 * reads or writes anything else.
 *
 * <p>The liveness filter ({@code tenant_deleted_at IS NULL}) keeps the derived scope aligned with
 * every caller-authorized scope: a soft-deleted tenant is excluded from all memberships for its
 * whole retention grace period ({@code TenantRepository#findTenantsByUserId}, {@code
 * TenantMembershipCacheManager}), so no operator can reach the run - the run-derived service scope
 * must not quietly re-admit it. A run in a soft-deleted tenant therefore resolves empty, exactly
 * like an unknown run (fail-closed, 404 at the callback); reactivating the tenant within the grace
 * period restores the callbacks with it.
 *
 * <p>Runs before the request's {@code @Transactional} boundary opens, so it acquires its own
 * connection ({@link JdbcTemplate} via {@code DataSourceUtils}); when a transaction is already
 * bound to the thread (integration tests) it joins it and sees the seeded run, and in production it
 * reads the already-committed run row.
 *
 * <p>This is the read-only scope-bootstrap exception of the tenant-activation runbook: the
 * exemption stays legitimate only while the class emits nothing but this single-row,
 * primary-key-addressed SELECT of the run's own {@code tenant_id}, filtered on the owning tenant's
 * liveness. {@code AutonomousRunTenantLocatorTest} pins the class list and the exact statement on
 * every build, the same enforcement pattern as the insert-only seed exemption ({@code
 * AttackPathSeedServiceTest}).
 */
@Component
@RequiredArgsConstructor
@AllowRawJdbc(
    reason =
        "Resolves the parent autonomous run's own tenant_id by primary key to derive the"
            + " service-identity scope for the orchestrator callback endpoints, BEFORE the"
            + " transaction (and thus the scope) is opened. This read is what SETS the scope, so it"
            + " cannot be tenant-scoped itself; it must bypass the inspector. It reads only the run's"
            + " immutable tenant_id by primary key, filtered on the owning tenant's liveness"
            + " (tenant_deleted_at IS NULL, the same predicate that keeps soft-deleted tenants out of"
            + " every caller scope), and touches nothing else; the exemption is pinned read-only by"
            + " AutonomousRunTenantLocatorTest.")
public class AutonomousRunTenantLocator {

  /**
   * The one statement this class may ever emit: a single-row read of the run's own tenant
   * attribution, addressed by primary key, projecting nothing else, and refusing a run whose owning
   * tenant is soft-deleted (its grace period excludes it from every caller scope, so the
   * run-derived scope must not re-admit it). Pinned verbatim by {@code
   * AutonomousRunTenantLocatorTest} so any widening fails the build.
   */
  static final String SELECT_RUN_TENANT =
      "SELECT r.tenant_id FROM autonomous_runs r JOIN tenants t ON t.tenant_id = r.tenant_id"
          + " WHERE r.autonomous_run_id = ? AND t.tenant_deleted_at IS NULL";

  private final JdbcTemplate jdbcTemplate;

  /**
   * The tenant that owns the run, or empty when no such run exists or its owning tenant is
   * soft-deleted (grace-period tenants are out of every caller scope, so the run-derived scope
   * refuses them too). A present-but-blank/null tenant is also returned as empty so the caller
   * fails closed.
   */
  public Optional<String> findRunTenant(String runId) {
    if (runId == null || runId.isBlank()) {
      return Optional.empty();
    }
    return jdbcTemplate.query(SELECT_RUN_TENANT, (rs, rowNum) -> rs.getString(1), runId).stream()
        .findFirst()
        .filter(tenant -> tenant != null && !tenant.isBlank());
  }
}
