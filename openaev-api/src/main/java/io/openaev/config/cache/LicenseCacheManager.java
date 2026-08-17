package io.openaev.config.cache;

import io.openaev.ee.EnterpriseEditionService;
import io.openaev.ee.License;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class LicenseCacheManager {
  private final EnterpriseEditionService enterpriseEditionService;
  private final ApplicationEventPublisher eventPublisher;

  public LicenseCacheManager(
      EnterpriseEditionService enterpriseEditionService, ApplicationEventPublisher eventPublisher) {
    this.enterpriseEditionService = enterpriseEditionService;
    this.eventPublisher = eventPublisher;
  }

  @Cacheable("license")
  public License getEnterpriseEditionInfo() {
    return enterpriseEditionService.getEnterpriseEditionInfo();
  }

  /**
   * Evicts the license cache (before the method body runs) and then publishes a {@link
   * LicenseRefreshedEvent} so that listeners (e.g. {@code LogService}) can react with guaranteed
   * fresh cache data.
   *
   * <p>{@code beforeInvocation = true} is intentional: it ensures the cache is already cleared when
   * the event handler calls {@link #getEnterpriseEditionInfo()}, avoiding a stale cache read and
   * the Hibernate {@code StaleStateException} caused by self-call proxy bypass.
   */
  @CacheEvict(value = "license", allEntries = true, beforeInvocation = true)
  public void refreshAndNotify() {
    eventPublisher.publishEvent(new LicenseRefreshedEvent(this));
  }
}
