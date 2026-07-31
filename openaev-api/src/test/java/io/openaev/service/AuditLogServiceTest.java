package io.openaev.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.database.model.BannerMessage;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.ee.License;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService")
class AuditLogServiceTest {

  @Mock private AuditLogger auditLogger;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private PlatformSettingsService platformSettingsService;
  @Mock private License license;

  @InjectMocks private AuditLogService auditLogService;

  @Nested
  @DisplayName("checkLicenseBanner()")
  class CheckLicenseBanner {

    @Test
    @DisplayName("given_auditFlagDisabled_should_cleanBanner")
    void given_auditFlagDisabled_should_cleanBanner() {
      // -- PREPARE --
      when(auditLogger.isAuditLoggingEnabled()).thenReturn(false);

      // -- EXECUTE --
      auditLogService.checkLicenseBanner();

      // -- CHECK --
      verify(platformSettingsService)
          .cleanMessage(eq(BannerMessage.BANNER_KEYS.AUDIT_LOG_NO_ENTERPRISE_LICENSE));
      verify(platformSettingsService, never()).errorMessage(any());
    }

    @Test
    @DisplayName("given_auditFlagEnabled_and_licenseActive_should_cleanBanner")
    void given_auditFlagEnabled_and_licenseActive_should_cleanBanner() {
      // -- PREPARE --
      when(auditLogger.isAuditLoggingEnabled()).thenReturn(true);
      when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(license);
      when(enterpriseEditionService.isLicenseActive(license)).thenReturn(true);

      // -- EXECUTE --
      auditLogService.checkLicenseBanner();

      // -- CHECK --
      verify(platformSettingsService)
          .cleanMessage(eq(BannerMessage.BANNER_KEYS.AUDIT_LOG_NO_ENTERPRISE_LICENSE));
      verify(platformSettingsService, never()).errorMessage(any());
    }

    @Test
    @DisplayName("given_auditFlagEnabled_and_licenseInactive_should_showBanner")
    void given_auditFlagEnabled_and_licenseInactive_should_showBanner() {
      // -- PREPARE --
      when(auditLogger.isAuditLoggingEnabled()).thenReturn(true);
      when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(license);
      when(enterpriseEditionService.isLicenseActive(license)).thenReturn(false);

      // -- EXECUTE --
      auditLogService.checkLicenseBanner();

      // -- CHECK --
      verify(platformSettingsService)
          .errorMessage(eq(BannerMessage.BANNER_KEYS.AUDIT_LOG_NO_ENTERPRISE_LICENSE));
      verify(platformSettingsService, never()).cleanMessage(any());
    }
  }
}
