package io.openaev.notification.engine;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.NotificationEventRecordRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Purges old {@link io.openaev.database.model.NotificationEventRecord} outbox rows. The retention
 * window must cover the largest digest period (one month), bounding the outbox like OpenCTI's
 * notification stream trimming.
 *
 * <p>Runs under {@code allTenants()}: one bulk delete by predicate spanning every tenant. The
 * inspector rewrites DELETE as it rewrites SELECT, so the scope decides how far the purge reaches -
 * with no scope it matches nothing and still reports success, and with a single tenant's scope it
 * silently leaves every other tenant's outbox growing. No {@code @Transactional} here: this is
 * background work reached from a Quartz job, where the self-invocation trap would skip both the
 * transaction and the scope.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationEventRetentionService {

  @Value("${openaev.notifications.event-retention-days:35}")
  private int retentionDays;

  private final NotificationEventRecordRepository notificationEventRecordRepository;
  private final TenantScopedTransaction tenantTx;

  public void deleteOldEvents() {
    Instant threshold = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    int deleted =
        tenantTx.<Integer>execute(
            TxCtx.allTenants(),
            () -> notificationEventRecordRepository.deleteAllByCreatedAtBefore(threshold));
    if (deleted > 0) {
      log.info("Purged {} notification events older than {} days", deleted, retentionDays);
    }
  }
}
