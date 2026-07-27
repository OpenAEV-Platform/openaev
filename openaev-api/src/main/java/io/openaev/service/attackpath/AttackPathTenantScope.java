package io.openaev.service.attackpath;

import io.openaev.context.TxCtx;
import java.util.List;

/**
 * The request's tenant ids, for the one attack-path table the statement inspector cannot filter:
 * {@code attackpath_graph_version} (its bump is an {@code INSERT ... ON CONFLICT}, so the table is
 * not tenant-active and its reads must carry the scope themselves, see {@link
 * io.openaev.database.model.attackpath.AttackPathGraphVersion}).
 *
 * <p>Every other attack-path read stays inspector-filtered and never needs this. An unresolved or
 * missing scope yields an empty list, which the callers treat as "no counter", i.e. fail closed.
 */
public final class AttackPathTenantScope {

  private AttackPathTenantScope() {}

  public static List<String> tenantIds(TxCtx scope) {
    return switch (scope) {
      case TxCtx.Restricted restricted -> restricted.tenantIds();
      // Missing is fail-closed by definition; AllTenants is an unresolved intention that never
      // reaches an HTTP read (TenantScopedTransaction is the only thing that resolves it).
      case TxCtx.Missing ignored -> List.of();
      case TxCtx.AllTenants ignored -> List.of();
    };
  }
}
