package io.openaev.notification.engine;

import io.openaev.database.repository.NotificationEventRecordRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges old {@link io.openaev.database.model.NotificationEventRecord} outbox rows. The retention
 * window must cover the largest digest period (one month), bounding the outbox like OpenCTI's
 * notification stream trimming.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationEventRetentionService {

  @Value("${openaev.notifications.event-retention-days:35}")
  private int retentionDays;

  private final NotificationEventRecordRepository notificationEventRecordRepository;
  private final EntityManager entityManager;

  @Transactional
  public void deleteOldEvents() {
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    Instant threshold = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    int deleted = notificationEventRecordRepository.deleteAllByCreatedAtBefore(threshold);
    if (deleted > 0) {
      log.info("Purged {} notification events older than {} days", deleted, retentionDays);
    }
  }
}
