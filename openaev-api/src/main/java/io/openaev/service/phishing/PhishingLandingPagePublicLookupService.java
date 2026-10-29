package io.openaev.service.phishing;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.repository.PhishingLandingPageRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one deliberately out-of-scope read behind the public phishing tracking endpoints: {@code
 * HostedPublicApi} resolves the recipient's tenant from the opaque per-recipient token, but {@code
 * TxCtxArgumentResolver} still resolves that anonymous request's own {@code TxCtx} to {@link
 * TxCtx#missing()} (no {@code {tenantId}} path segment), and {@code TenantScopeTransactionAspect}
 * locks that empty scope for the whole HTTP transaction. A lazy load of {@code
 * phishing_landing_pages} inside that transaction would therefore always come back empty. Kept
 * outside {@code io.openaev.api../io.openaev.rest..} because {@code TenantScopedTransaction} is a
 * background-only primitive (enforced by {@code
 * TenantBackgroundTransactionRules.NO_PRIMITIVE_ON_HTTP_PATH}); same shape as {@code
 * CustomDomainPublicLookupService}, but scoped to the one recipient's own tenant rather than every
 * tenant, since the token already resolved it.
 */
@Service
@RequiredArgsConstructor
public class PhishingLandingPagePublicLookupService {

  private final PhishingLandingPageRepository phishingLandingPageRepository;
  private final TenantScopedTransaction tenantTx;

  @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
  public Optional<PhishingLandingPage> byId(String tenantId, String landingPageId) {
    if (tenantId == null || landingPageId == null) {
      return Optional.empty();
    }
    return tenantTx.execute(
        TxCtx.forTenant(tenantId), () -> phishingLandingPageRepository.findById(landingPageId));
  }
}
