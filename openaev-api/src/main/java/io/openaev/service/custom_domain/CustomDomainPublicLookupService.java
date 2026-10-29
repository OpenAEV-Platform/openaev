package io.openaev.service.custom_domain;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.CustomDomainRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one deliberately cross-tenant read behind custom domains: resolving a hostname's status for
 * the unauthenticated {@code domain-check} endpoint, which by construction has no tenant of its own
 * to scope the read with. Kept outside {@code io.openaev.api../io.openaev.rest..} because {@code
 * TenantScopedTransaction} is a background-only primitive (enforced by {@code
 * TenantBackgroundTransactionRules.NO_PRIMITIVE_ON_HTTP_PATH}); {@code
 * CustomDomainService.isHostnameVerified} calls this bean instead of opening the scope itself, same
 * shape as {@code CredentialService#globalCount}.
 */
@Service
@RequiredArgsConstructor
public class CustomDomainPublicLookupService {

  private final CustomDomainRepository customDomainRepository;
  private final TenantScopedTransaction tenantTx;

  @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
  public Optional<String> statusByHostname(String hostname) {
    return tenantTx.execute(
        TxCtx.allTenants(), () -> customDomainRepository.findStatusByHostname(hostname));
  }
}
