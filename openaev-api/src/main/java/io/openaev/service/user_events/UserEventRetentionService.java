package io.openaev.service.user_events;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.UserEventType;
import io.openaev.database.repository.UserEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventRetentionService {

  private final UserEventRepository userEventRepository;
  private final UserEventRetentionConfig settingsService;
  private final TenantScopedTransaction tenantScopedTransaction;

  /**
   * Applies retention rules to user events. Runs from a Quartz job with no ambient tenant scope;
   * the retention settings it reads are platform rows of the dual-scope {@code parameters} table,
   * which need a non-empty scope to be visible at all ({@code user_events} itself is not a
   * v2-active table, so joining this scope onto the current transaction has no effect on it).
   */
  @Transactional
  public void deleteOldEvents() {
    tenantScopedTransaction.setScopeOnCurrentTransaction(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID));
    if (!settingsService.isEnabled()) {
      log.debug("UserEvent retention disabled");
      return;
    }

    for (UserEventType type : UserEventType.values()) {
      int days = settingsService.getRetentionDays(type);
      if (days <= 0) {
        log.warn("Retention disabled for type {} (days={})", type, days);
        continue;
      }

      Instant before = Instant.now().minus(days, ChronoUnit.DAYS);
      int deleted = userEventRepository.deleteOlderThan(type, before);
      if (deleted > 0) {
        log.info(
            "UserEvent retention: deleted {} events of type {} older than {} days",
            deleted,
            type,
            days);
      }
    }
  }
}
