package io.openaev.injectors.phishing.service;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.PhishingResultRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one deliberately cross-tenant read behind phishing tracking: resolving a token's owning
 * tenant for the unauthenticated tracking endpoints, which by construction have no tenant of their
 * own to scope the read with. Kept outside {@code io.openaev.api../io.openaev.rest..} because
 * {@code TenantScopedTransaction} is a background-only primitive (enforced by {@code
 * TenantBackgroundTransactionRules.NO_PRIMITIVE_ON_HTTP_PATH}); {@code
 * PhishingTrackingService.resolveTenantIdByToken} calls this bean instead of opening the scope
 * itself, same shape as {@code CustomDomainService.isHostnameVerified} / {@code
 * CustomDomainPublicLookupService}.
 */
@Service
@RequiredArgsConstructor
public class PhishingTrackingPublicLookupService {

  private final PhishingResultRepository phishingResultRepository;
  private final TenantScopedTransaction tenantTx;

  @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
  public Optional<String> tenantIdByToken(String token) {
    return tenantTx.execute(
        TxCtx.allTenants(), () -> phishingResultRepository.findTenantIdByToken(token));
  }
}
