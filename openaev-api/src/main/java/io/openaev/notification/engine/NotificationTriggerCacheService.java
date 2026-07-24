package io.openaev.notification.engine;

import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Short-TTL, cross-tenant cache of enabled live notification triggers grouped by watched resource
 * type. Keeps the per-event hot path of the notification engine free of trigger queries (the
 * OpenAEV equivalent of OpenCTI's cache-resident trigger map).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationTriggerCacheService {

  private static final Duration CACHE_TTL = Duration.ofSeconds(30);
  private static final Duration RETRY_TTL = Duration.ofSeconds(5);

  private final NotificationTriggerLoader notificationTriggerLoader;

  private volatile Map<ResourceType, List<ResolvedNotificationTrigger>> cache = Map.of();
  private volatile Instant cacheExpiry = Instant.EPOCH;

  /** Returns the enabled live triggers watching the given resource type (cross-tenant). */
  public List<ResolvedNotificationTrigger> getLiveTriggers(ResourceType resourceType) {
    if (Instant.now().isAfter(cacheExpiry)) {
      refresh();
    }
    return cache.getOrDefault(resourceType, List.of());
  }

  /** Invalidates the cache (called on trigger CRUD so changes apply immediately). */
  public void invalidate() {
    cacheExpiry = Instant.EPOCH;
  }

  private synchronized void refresh() {
    if (Instant.now().isBefore(cacheExpiry)) {
      return; // refreshed concurrently
    }
    try {
      List<ResolvedNotificationTrigger> resolved =
          notificationTriggerLoader.loadEnabledTriggers(NotificationTriggerType.LIVE).stream()
              .filter(trigger -> trigger.watchedResourceType() != null)
              .toList();
      this.cache =
          resolved.stream()
              .collect(Collectors.groupingBy(ResolvedNotificationTrigger::watchedResourceType));
      this.cacheExpiry = Instant.now().plus(CACHE_TTL);
    } catch (Exception e) {
      log.error("Failed to refresh notification trigger cache", e);
      // keep serving the stale cache, retry shortly
      this.cacheExpiry = Instant.now().plus(RETRY_TTL);
    }
  }
}
