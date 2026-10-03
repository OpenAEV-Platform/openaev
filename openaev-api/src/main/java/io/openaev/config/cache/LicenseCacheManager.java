package io.openaev.config.cache;

import io.openaev.ee.EnterpriseEditionService;
import io.openaev.ee.License;
import io.openaev.xtmone.XtmOneEntitlementService;
import java.time.Instant;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * The one Enterprise Edition decision of the platform: this platform's own valid OpenAEV license
 * or, when it has none, a verified XTM license of the connected XTM One that sub-licenses it (see
 * {@link XtmOneEntitlementService}). Every Enterprise Edition gate and the license settings read
 * {@link #getEnterpriseEditionInfo()}; the own license takes precedence when both are valid.
 */
@Service
public class LicenseCacheManager {

  static final String LICENSE_CACHE = "license";
  private static final String OWN_LICENSE_KEY = "openaev";

  private final EnterpriseEditionService enterpriseEditionService;
  private final XtmOneEntitlementService xtmOneEntitlementService;
  private final CacheManager cacheManager;
  private final ApplicationEventPublisher eventPublisher;

  public LicenseCacheManager(
      EnterpriseEditionService enterpriseEditionService,
      XtmOneEntitlementService xtmOneEntitlementService,
      CacheManager cacheManager,
      ApplicationEventPublisher eventPublisher) {
    this.enterpriseEditionService = enterpriseEditionService;
    this.xtmOneEntitlementService = xtmOneEntitlementService;
    this.cacheManager = cacheManager;
    this.eventPublisher = eventPublisher;
  }

  /**
   * The license that decides Enterprise Edition, its {@code license_is_validated} flag being the
   * gates' decision at this instant (the frontend gates read that flag). The own license is cached
   * (parsing and verifying it is costly); the XTM license is read on every call, since its dates
   * are re-applied each time.
   */
  public License getEnterpriseEditionInfo() {
    License own = ownLicense();
    if (enterpriseEditionService.isLicenseActive(own)) {
      return own;
    }
    return xtmOneEntitlementService
        .activeLicense()
        .map(xtmLicense -> xtmLicense.toLicense(Instant.now()))
        .orElseGet(() -> own.isLicenseValidated() ? noLongerGranting(own) : own);
  }

  /** Whether Enterprise Edition is in force now, as every gate decides it. */
  public boolean isEnterpriseEditionActive() {
    return enterpriseEditionService.isLicenseActive(getEnterpriseEditionInfo());
  }

  /**
   * An own license past its expiration date that no longer grants still reads as validated when it
   * was cached before that date, or when it is a trial (no grace period). The copy reads as the
   * gates decide; the cached license is shared and left as parsed.
   */
  private static License noLongerGranting(License own) {
    License license = new License();
    BeanUtils.copyProperties(own, license);
    license.setLicenseValidated(false);
    license.setLicenseExpired(true);
    return license;
  }

  private License ownLicense() {
    Cache cache = cacheManager.getCache(LICENSE_CACHE);
    if (cache == null) {
      return enterpriseEditionService.getEnterpriseEditionInfo();
    }
    return cache.get(OWN_LICENSE_KEY, enterpriseEditionService::getEnterpriseEditionInfo);
  }

  /**
   * Evicts the license cache (before the method body runs) and then publishes a {@link
   * LicenseRefreshedEvent} so that listeners (e.g. {@code LogService}) can react with guaranteed
   * fresh cache data. Also called when the XTM license in force changes.
   *
   * <p>{@code beforeInvocation = true} is intentional: it ensures the cache is already cleared when
   * the event handler calls {@link #getEnterpriseEditionInfo()}, avoiding a stale cache read and
   * the Hibernate {@code StaleStateException} caused by self-call proxy bypass.
   */
  @CacheEvict(value = LICENSE_CACHE, allEntries = true, beforeInvocation = true)
  public void refreshAndNotify() {
    eventPublisher.publishEvent(new LicenseRefreshedEvent(this));
  }
}
