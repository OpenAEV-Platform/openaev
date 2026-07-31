package io.openaev.service;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.BannerMessage;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.aop.audit_log.AuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for audit-log health checks.
 *
 * <p>Displays a platform banner when audit logging is enabled (via the {@code AUDIT_LOG} feature
 * flag) but the Enterprise Edition license is absent or inactive. Clears the banner as soon as the
 * situation is resolved.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

  private final AuditLogger auditLogger;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final PlatformSettingsService platformSettingsService;

  // -- OPTIONS --

  /**
   * Checks whether audit logging is enabled and, if so, whether an active Enterprise Edition license
   * is present.
   *
   * <ul>
   *   <li>If the feature flag is ON but the EE license is absent/inactive → shows the {@code
   *       AUDIT_LOG_NO_ENTERPRISE_LICENSE} banner.
   *   <li>Otherwise → clears that banner.
   * </ul>
   */
  @Transactional
  public void checkLicenseBanner() {
    boolean isAuditFlagEnabled = auditLogger.isAuditLoggingEnabled();
    if (!isAuditFlagEnabled) {
      // Feature not enabled: nothing to warn about.
      platformSettingsService.cleanMessage(
          BannerMessage.BANNER_KEYS.AUDIT_LOG_NO_ENTERPRISE_LICENSE);
      return;
    }

    boolean isEeActive =
        enterpriseEditionService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo());
    if (isEeActive) {
      platformSettingsService.cleanMessage(
          BannerMessage.BANNER_KEYS.AUDIT_LOG_NO_ENTERPRISE_LICENSE);
    } else {
      log.warn(
          "[AUDIT] Audit logging is enabled but inactive — an Enterprise Edition license is required.");
      platformSettingsService.errorMessage(
          BannerMessage.BANNER_KEYS.AUDIT_LOG_NO_ENTERPRISE_LICENSE);
    }
  }
}
