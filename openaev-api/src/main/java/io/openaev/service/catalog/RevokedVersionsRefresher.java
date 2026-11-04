package io.openaev.service.catalog;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.notification.content.ContentNotificationEvents.ListRevocationsChanged;
import io.openaev.notification.content.ContentNotificationEvents.ListedVersion;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.service.tenants.TenantService;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A revocation list applies to every tenant: once it is committed, each tenant moves the latest
 * version of the payloads it revoked or lifted.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RevokedVersionsRefresher {

  private final TenantScopedTransaction tenantTx;
  private final TenantService tenantService;
  private final PayloadVersionService payloadVersionService;

  @TransactionalEventListener
  public void onListRevocationsChanged(ListRevocationsChanged event) {
    Set<String> externalIds =
        Stream.concat(event.revoked().stream(), event.lifted().stream())
            .map(ListedVersion::externalId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    if (externalIds.isEmpty()) {
      return;
    }
    String current = TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    try {
      for (String tenantId : tenantService.findActiveTenantIds()) {
        TenantContext.setCurrentTenant(tenantId);
        try {
          tenantTx.executeNew(
              TxCtx.forTenant(tenantId),
              () -> externalIds.forEach(id -> refresh(event.repository(), id)));
        } catch (RuntimeException e) {
          log.error("Latest versions not refreshed in tenant {}", tenantId, e);
        }
      }
    } finally {
      if (current == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(current);
      }
    }
  }

  private void refresh(String repository, String externalId) {
    payloadVersionService
        .findManagedIdentity(repository, externalId)
        .ifPresent(
            identity -> {
              payloadVersionService.refreshLatest(identity);
              payloadVersionService
                  .findVariantsOf(identity)
                  .forEach(payloadVersionService::refreshLatest);
            });
  }
}
