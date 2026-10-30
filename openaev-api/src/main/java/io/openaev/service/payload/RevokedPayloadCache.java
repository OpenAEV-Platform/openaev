package io.openaev.service.payload;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.openaev.notification.content.ContentNotificationEvents.ListRevocationsChanged;
import io.openaev.notification.content.ContentNotificationEvents.PayloadRevocationChanged;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Whether a payload is revoked, for health checks only: running a payload always reads the
 * revocations. Emptied after every revocation change; the short expiry lets the other replicas
 * catch up.
 */
@Component
public class RevokedPayloadCache {

  private final Cache<String, Boolean> revoked =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).maximumSize(50_000).build();

  public boolean isRevoked(String tenantId, String payloadId, Supplier<Boolean> load) {
    return revoked.get(tenantId + ":" + payloadId, key -> load.get());
  }

  public void put(String tenantId, String payloadId, boolean isRevoked) {
    revoked.put(tenantId + ":" + payloadId, isRevoked);
  }

  // Emptied at once for the changing transaction, and again after commit for the others.
  @EventListener({PayloadRevocationChanged.class, ListRevocationsChanged.class})
  public void onChange() {
    revoked.invalidateAll();
  }

  @TransactionalEventListener(
      classes = {PayloadRevocationChanged.class, ListRevocationsChanged.class},
      fallbackExecution = true)
  public void afterChange() {
    revoked.invalidateAll();
  }
}
