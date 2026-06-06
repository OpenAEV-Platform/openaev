package io.openaev.database.repository;

import io.openaev.context.OperationState;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantProxy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tenant-aware repository facade for {@link io.openaev.database.model.Document}.
 *
 * <p>This class replaces direct injection of the Spring Data interface. It acts as a factory: you
 * must call {@link #forTenant(OperationState)} to obtain a tenant-scoped view of the repository.
 * Calling any persistence method without providing an {@link OperationState} is a
 * <strong>compile-time error</strong>.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // In a service or controller:
 * documentRepository.forTenant(operationState).findById(id);
 * documentRepository.forTenant(operationState).rawAllDocuments();
 *
 * // In a parallel stream — no TenantExecutionContext.run() needed:
 * ids.parallelStream()
 *    .map(id -> documentRepository.forTenant(operationState).findById(id))
 *    .toList();
 * }</pre>
 *
 * <h3>Legacy / internal callers</h3>
 *
 * <p>Internal services (jobs, connectors) that do not yet carry an {@link OperationState} may use
 * {@link #forCurrentTenant()} as a temporary bridge. Migrate them to {@link
 * #forTenant(OperationState)} to make the tenant contract explicit.
 */
@Component
@RequiredArgsConstructor
public class DocumentRepository {

  /** Internal Spring Data bean — never injected or used directly by external classes. */
  private final DocumentJpaRepository internal;

  /**
   * Returns a tenant-scoped proxy. Every call on the returned object automatically sets {@link
   * io.openaev.context.TenantExecutionContext} from {@code operationState}, so the {@link
   * io.openaev.config.TenantStatementInspector} adds the correct {@code WHERE tenant_id} clause to
   * all SQL statements.
   *
   * @param operationState tenant scope for all repository calls
   * @return a proxy of {@link DocumentJpaRepository} scoped to the provided tenant(s)
   */
  public DocumentJpaRepository forTenant(OperationState operationState) {
    return TenantProxy.of(internal, DocumentJpaRepository.class, operationState);
  }

  /**
   * Bridge for legacy callers that do not yet carry an {@link OperationState}. Reads the current
   * tenant from {@link TenantContext} (set by the HTTP interceptor or the job scheduler).
   *
   * @deprecated Migrate callers to {@link #forTenant(OperationState)} to make the tenant contract
   *     explicit and safe for parallel execution.
   */
  @Deprecated(since = "migration", forRemoval = true)
  public DocumentJpaRepository forCurrentTenant() {
    return forTenant(OperationState.of(TenantContext.getCurrentTenant()));
  }
}
