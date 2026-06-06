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
 * documentRepository.forOp(state).findById(id);
 * documentRepository.forOp(state).rawAllDocuments();
 *
 * // In a parallel stream — no TenantExecutionContext.run() needed:
 * ids.parallelStream()
 *    .map(id -> documentRepository.forOp(state).findById(id))
 *    .toList();
 * }</pre>
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

}
