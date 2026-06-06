package io.openaev.database.repository;

import io.openaev.context.ExecState;
import io.openaev.context.StateExecutionContext;
import io.openaev.context.TenantProxy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tenant-aware repository facade for {@link io.openaev.database.model.Document}.
 *
 * <p>This class replaces direct injection of the Spring Data interface. It acts as a factory: you
 * must call {@link #forOp(ExecState)} to obtain a tenant-scoped view of the repository. Calling any
 * persistence method without providing an {@link ExecState} is a <strong>compile-time
 * error</strong>.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // In a service or controller:
 * documentRepository.forTenant(state).findById(id);
 * documentRepository.forTenant(state).rawAllDocuments();
 *
 * // In a parallel stream — no TenantExecutionContext.run() needed:
 * ids.parallelStream()
 *    .map(id -> documentRepository.forTenant(state).findById(id))
 *    .toList();
 * }</pre>
 *
 * <h3>Legacy / internal callers</h3>
 *
 * <p>Internal services (jobs, connectors) that do not yet carry an {@link ExecState} may use {@link
 * #forCurrentTenant()} as a temporary bridge. Migrate them to {@link #forOp(ExecState)} to make the
 * tenant contract explicit.
 */
@Component
@RequiredArgsConstructor
public class DocumentRepository {

  /** Internal Spring Data bean — never injected or used directly by external classes. */
  private final DocumentJpaRepository internal;

  /**
   * Returns a tenant-scoped proxy. Every call on the returned object automatically sets {@link
   * StateExecutionContext} from {@code state}, so the {@link
   * io.openaev.config.TenantStatementInspector} adds the correct {@code WHERE tenant_id} clause to
   * all SQL statements.
   *
   * @param state tenant scope for all repository calls
   * @return a proxy of {@link DocumentJpaRepository} scoped to the provided tenant(s)
   */
  public DocumentJpaRepository forOp(ExecState state) {
    return TenantProxy.of(internal, DocumentJpaRepository.class, state);
  }

  /**
   * Bridge for legacy callers that do not yet carry an {@link ExecState}. Uses the {@link
   * ExecState} already set in the {@link StateExecutionContext} by the interceptor or caller.
   *
   * @deprecated Migrate callers to {@link #forOp(ExecState)} to make the tenant contract explicit
   *     and safe for parallel execution.
   */
  @Deprecated(since = "migration", forRemoval = true)
  public DocumentJpaRepository forCurrentTenant() {
    ExecState state = StateExecutionContext.get();
    if (state == null) {
      throw new IllegalStateException(
          "No TenantExecutionContext active — use forOp(ExecState) instead");
    }
    return forOp(state);
  }
}
