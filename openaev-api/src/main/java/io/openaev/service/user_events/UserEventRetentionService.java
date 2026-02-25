package io.openaev.service.user_events;

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

  /** Applies retention rules to user events. */
  @Transactional
  public void deleteOldEvents() {
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
