package io.openaev.service.comcheck;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ComcheckStatusRepository;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one read across tenants behind the public comcheck route: resolving the tenant of the
 * simulation a comcheck status belongs to. The route has no tenant in its path and its caller is
 * anonymous, so its scope holds no tenant. Kept outside {@code io.openaev.api..} and {@code
 * io.openaev.rest..} because {@code TenantScopedTransaction} is a background primitive (enforced by
 * {@code TenantBackgroundTransactionRules.NO_PRIMITIVE_ON_HTTP_PATH}), same shape as {@code
 * PhishingTrackingPublicLookupService}.
 */
@Service
@RequiredArgsConstructor
public class ComcheckPublicLookupService {

  private final ComcheckStatusRepository comcheckStatusRepository;
  private final TenantScopedTransaction tenantTx;

  /**
   * The tenant of the simulation of the comcheck the status belongs to, the default tenant for a
   * comcheck with no simulation (the comcheck job places such a comcheck there too), empty for an
   * unknown status.
   */
  @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
  public Optional<String> tenantOfStatus(String statusId) {
    return tenantTx
        .execute(
            TxCtx.allTenants(),
            () -> comcheckStatusRepository.findComcheckWithTenantByStatusId(statusId))
        .map(
            comcheck ->
                Objects.requireNonNullElse(comcheck.getTenant_id(), Tenant.DEFAULT_TENANT_UUID));
  }
}
