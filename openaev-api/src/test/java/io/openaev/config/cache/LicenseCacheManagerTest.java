package io.openaev.config.cache;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.aop.AccessControl;
import io.openaev.aop.AccessControlAspect;
import io.openaev.ee.EnterpriseEditionException;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.ee.License;
import io.openaev.ee.LicenseSource;
import io.openaev.ee.LicenseTypeEnum;
import io.openaev.ee.XtmLicense;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.xtmone.XtmOneEntitlementService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("Enterprise Edition decision")
class LicenseCacheManagerTest {

  private static final String PLATFORM_ID = "4a0f1a6e-5b6e-4a51-8f6a-0d6b2b0c9a11";

  private final EnterpriseEditionService enterpriseEditionService =
      mock(EnterpriseEditionService.class);
  private final XtmOneEntitlementService xtmOneEntitlementService =
      mock(XtmOneEntitlementService.class);
  private LicenseCacheManager licenseCacheManager;
  private AccessControlAspect aspect;

  @SuppressWarnings("unused")
  private static final class Endpoints {

    @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
    void enterpriseFeature() {}
  }

  @BeforeEach
  void setUp() {
    when(enterpriseEditionService.isLicenseActive(any())).thenCallRealMethod();
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenCallRealMethod();
    licenseCacheManager =
        new LicenseCacheManager(
            enterpriseEditionService,
            xtmOneEntitlementService,
            new ConcurrentMapCacheManager(LicenseCacheManager.LICENSE_CACHE),
            mock(ApplicationEventPublisher.class));
    aspect =
        new AccessControlAspect(
            mock(PermissionService.class),
            mock(UserService.class),
            enterpriseEditionService,
            licenseCacheManager);
  }

  // -- Helpers --

  private static License ownLicense(boolean valid) {
    License license = new License();
    if (valid) {
      license.setLicenseEnterprise(true);
      license.setLicenseValidated(true);
      license.setType(LicenseTypeEnum.standard);
      license.setCustomer("OpenAEV customer");
      license.setExpirationDate(Instant.now().plus(100, DAYS));
    }
    return license;
  }

  private static XtmLicense xtmLicense(String type, Instant start, Instant end) {
    return new XtmLicense(type, "XTM customer", start, end, "global");
  }

  private void givenOwnLicense(boolean valid) {
    when(enterpriseEditionService.getEnterpriseEditionInfo()).thenReturn(ownLicense(valid));
  }

  private void givenXtmLicense(XtmLicense license) {
    when(xtmOneEntitlementService.activeLicense()).thenReturn(Optional.ofNullable(license));
  }

  private void callEnterpriseFeature() throws Throwable {
    AccessControl accessControl =
        Endpoints.class.getDeclaredMethod("enterpriseFeature").getAnnotation(AccessControl.class);
    aspect.methodRBACVerification(mock(JoinPoint.class), accessControl);
  }

  // -- Tests --

  @Nested
  @DisplayName("Source of Enterprise Edition")
  class Source {

    @Test
    @DisplayName("Given a valid own license should decide on it, ahead of an XTM license")
    void given_validOwnLicense_should_takePrecedence() throws Throwable {
      // Arrange
      givenOwnLicense(true);

      // Act
      License license = licenseCacheManager.getEnterpriseEditionInfo();

      // Assert
      assertThat(license.getSource()).isEqualTo(LicenseSource.openaev);
      assertThat(license.getCustomer()).isEqualTo("OpenAEV customer");
      assertThat(licenseCacheManager.isEnterpriseEditionActive()).isTrue();
      assertThatCode(LicenseCacheManagerTest.this::callEnterpriseFeature)
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given no own license and a verified XTM license should grant Enterprise Edition")
    void given_verifiedXtmLicense_should_grantEnterpriseEdition() throws Throwable {
      // Arrange
      givenOwnLicense(false);
      givenXtmLicense(
          xtmLicense("standard", Instant.now().minus(10, DAYS), Instant.now().plus(300, DAYS)));

      // Act
      License license = licenseCacheManager.getEnterpriseEditionInfo();

      // Assert
      assertThat(license.getSource()).isEqualTo(LicenseSource.xtm_one);
      assertThat(license.isLicenseValidated()).isTrue();
      assertThat(license.getCustomer()).isEqualTo("XTM customer");
      assertThat(license.isGlobalLicense()).isTrue();
      assertThat(enterpriseEditionService.isLicenseActive(license)).isTrue();
      assertThat(licenseCacheManager.isEnterpriseEditionActive()).isTrue();
      assertThatCode(LicenseCacheManagerTest.this::callEnterpriseFeature)
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given no own license and no verified XTM license should be Community Edition")
    void given_noLicense_should_beCommunityEdition() {
      // Arrange
      givenOwnLicense(false);
      givenXtmLicense(null);

      // Act
      License license = licenseCacheManager.getEnterpriseEditionInfo();

      // Assert
      assertThat(license.getSource()).isEqualTo(LicenseSource.openaev);
      assertThat(enterpriseEditionService.isLicenseActive(license)).isFalse();
      assertThat(licenseCacheManager.isEnterpriseEditionActive()).isFalse();
      assertThatThrownBy(LicenseCacheManagerTest.this::callEnterpriseFeature)
          .isInstanceOf(EnterpriseEditionException.class);
    }

    @Test
    @DisplayName("Given a cached own license past its expiration date should no longer be in force")
    void given_expiredCachedOwnLicense_should_notBeInForce() {
      // Arrange: validated when it was parsed and cached, before its expiration date.
      License own = ownLicense(true);
      own.setExpirationDate(Instant.now().minus(1, DAYS));
      when(enterpriseEditionService.getEnterpriseEditionInfo()).thenReturn(own);
      givenXtmLicense(null);

      // Act
      License license = licenseCacheManager.getEnterpriseEditionInfo();

      // Assert: the cached flag is stale, the decision in force is not.
      assertThat(license.isLicenseValidated()).isTrue();
      assertThat(licenseCacheManager.isEnterpriseEditionActive()).isFalse();
      assertThatThrownBy(LicenseCacheManagerTest.this::callEnterpriseFeature)
          .isInstanceOf(EnterpriseEditionException.class);
    }

    @Test
    @DisplayName("Given an XTM license that lapsed should revert to Community Edition")
    void given_lapsedXtmLicense_should_revertToCommunityEdition() {
      // Arrange
      givenOwnLicense(false);
      givenXtmLicense(
          xtmLicense("standard", Instant.now().minus(10, DAYS), Instant.now().plus(300, DAYS)));
      licenseCacheManager.getEnterpriseEditionInfo();
      givenXtmLicense(null);

      // Act & Assert
      assertThatThrownBy(LicenseCacheManagerTest.this::callEnterpriseFeature)
          .isInstanceOf(EnterpriseEditionException.class);
    }

    @Test
    @DisplayName("Given the own license should parse it once and then serve it from the cache")
    void given_ownLicense_should_beCached() {
      // Arrange
      givenOwnLicense(true);

      // Act
      licenseCacheManager.getEnterpriseEditionInfo();
      licenseCacheManager.getEnterpriseEditionInfo();

      // Assert
      verify(enterpriseEditionService, times(1)).getEnterpriseEditionInfo();
    }
  }

  @Nested
  @DisplayName("XTM license as a platform license")
  class AsPlatformLicense {

    @Test
    @DisplayName("Given a standard license in its grace period should read as in grace")
    void given_gracePeriod_should_readAsExtraExpiration() {
      // Arrange
      Instant now = Instant.now();
      XtmLicense license = xtmLicense("standard", now.minus(400, DAYS), now.minus(30, DAYS));

      // Act
      License platformLicense = license.toLicense(now);

      // Assert
      assertThat(platformLicense.isLicenseValidated()).isTrue();
      assertThat(platformLicense.isLicenseExpired()).isTrue();
      assertThat(platformLicense.isExtraExpiration()).isTrue();
      assertThat(platformLicense.getExtraExpirationDays()).isBetween(59L, 60L);
      assertThat(platformLicense.getType()).isEqualTo(LicenseTypeEnum.standard);
    }

    @Test
    @DisplayName("Given a renewal issued ahead of its start date should read as valid, not expired")
    void given_earlyRenewal_should_readAsValidNotExpired() {
      // Arrange
      Instant now = Instant.now();
      XtmLicense license = xtmLicense("standard", now.plus(10, DAYS), now.plus(375, DAYS));

      // Act
      License platformLicense = license.toLicense(now);

      // Assert
      assertThat(platformLicense.isLicenseValidated()).isTrue();
      assertThat(platformLicense.isLicenseExpired()).isFalse();
      assertThat(platformLicense.isExtraExpiration()).isFalse();
      assertThat(enterpriseEditionService.isLicenseActive(platformLicense)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"standard", "lts", "nfr", "trial", "ci"})
    @DisplayName(
        "Given the instants around each date should agree with the Enterprise Edition gate")
    void given_boundaryInstants_should_agreeWithTheGate(String type) {
      // Arrange
      Instant start = Instant.parse("2026-01-01T00:00:00Z");
      Instant end = Instant.parse("2026-12-31T00:00:00Z");
      XtmLicense license = xtmLicense(type, start, end);
      List<Instant> instants =
          Stream.of(start, end, license.validUntil())
              .flatMap(at -> Stream.of(at.minusNanos(1), at, at.plusNanos(1)))
              .toList();

      // Act & Assert
      assertThat(license.isActiveAt(end))
          .as("XTM One still grants at the expiration instant")
          .isTrue();
      for (Instant at : instants) {
        assertThat(EnterpriseEditionService.isLicenseActiveAt(license.toLicense(at), at))
            .as("%s license at %s", type, at)
            .isEqualTo(license.isActiveAt(at));
      }
    }

    @Test
    @DisplayName("Given a ci license should read as a trial, the type without grace")
    void given_ciLicense_should_readAsTrial() {
      // Arrange
      Instant now = Instant.now();
      XtmLicense license = xtmLicense("ci", now.minus(1, DAYS), now.plus(1, DAYS));

      // Act
      License platformLicense = license.toLicense(now);

      // Assert
      assertThat(platformLicense.getType()).isEqualTo(LicenseTypeEnum.trial);
      assertThat(platformLicense.isLicenseValidated()).isTrue();
    }

    @Test
    @DisplayName("Given a license covering this platform id should report it as its platform")
    void given_platformGrant_should_reportThePlatformId() {
      // Arrange
      Instant now = Instant.now();
      XtmLicense license =
          new XtmLicense("lts", "XTM customer", now.minus(1, DAYS), now.plus(1, DAYS), PLATFORM_ID);

      // Act
      License platformLicense = license.toLicense(now);

      // Assert
      assertThat(platformLicense.getPlatform()).isEqualTo(PLATFORM_ID);
      assertThat(platformLicense.isGlobalLicense()).isFalse();
    }
  }
}
