package io.openaev.xtmone;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.openaev.ee.XtmLicense;
import io.openaev.ee.XtmLicenseException;
import io.openaev.ee.XtmLicenseVerifier;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

@DisplayName("XTM One entitlement")
class XtmOneEntitlementServiceTest {

  private static final String PLATFORM_ID = "4a0f1a6e-5b6e-4a51-8f6a-0d6b2b0c9a11";
  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final String PEM =
      "-----BEGIN CERTIFICATE-----\nMIIBAAAA\n-----END CERTIFICATE-----\n";

  private final XtmOneEntitlementService service = new XtmOneEntitlementService();
  private MockedStatic<XtmLicenseVerifier> verifier;
  private Logger logger;
  private ListAppender<ILoggingEvent> logs;

  @BeforeEach
  void setUp() {
    verifier = mockStatic(XtmLicenseVerifier.class);
    logger = (Logger) LoggerFactory.getLogger(XtmOneEntitlementService.class);
    logs = new ListAppender<>();
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logs);
    logs.stop();
    verifier.close();
  }

  // -- Helpers --

  private static Map<String, Object> answer(Object... keyValues) {
    Map<String, Object> answer = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      answer.put((String) keyValues[i], keyValues[i + 1]);
    }
    return answer;
  }

  private static XtmLicense trialLicenseEndingAt(Instant end) {
    return new XtmLicense("trial", "ACME", Instant.now().minus(1, DAYS), end, "global");
  }

  private void verifierReturns(XtmLicense license) {
    verifier
        .when(() -> XtmLicenseVerifier.verify(anyString(), anyString(), any(), any()))
        .thenReturn(license);
  }

  private boolean register(Map<String, Object> answer) {
    return service.onRegistrationAnswer(answer, PLATFORM_ID, CREATED_AT, false);
  }

  private List<ILoggingEvent> warnings() {
    return logs.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
  }

  // -- Tests --

  @Test
  @DisplayName("Given no registration answer yet should not be entitled")
  void given_noRegistrationAnswer_should_notBeEntitled() {
    assertThat(service.activeLicense().isPresent()).isFalse();
  }

  @Nested
  @DisplayName("Without a certificate")
  class WithoutCertificate {

    @Test
    @DisplayName("Given ee_enabled from an XTM One without the certificate should warn once")
    void given_eeEnabledWithoutCertificate_should_notEntitleAndWarnOnce() {
      // Act
      register(answer("ee_enabled", true));
      register(answer("ee_enabled", true));

      // Assert
      assertThat(service.activeLicense().isPresent()).isFalse();
      assertThat(warnings()).hasSize(1);
      assertThat(warnings().getFirst().getFormattedMessage())
          .contains("ee_enabled is advisory", "Upgrade XTM One", "xtm_license_pem");
      verifier.verifyNoInteractions();
    }

    @Test
    @DisplayName("Given ee_enabled explained by this platform's own license should not warn")
    void given_eeEnabledAndOwnLicense_should_notWarn() {
      // Act
      service.onRegistrationAnswer(answer("ee_enabled", true), PLATFORM_ID, CREATED_AT, true);

      // Assert
      assertThat(service.activeLicense().isPresent()).isFalse();
      assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("Given the xtm_sublicense source without its certificate should warn")
    void given_xtmSublicenseSourceWithoutCertificate_should_notEntitleAndWarn() {
      // Act
      register(
          answer(
              "ee_enabled",
              true,
              "ee_sources",
              List.of("xtm_sublicense"),
              "xtm_license_pem",
              null));

      // Assert
      assertThat(service.activeLicense().isPresent()).isFalse();
      assertThat(warnings()).hasSize(1);
      assertThat(warnings().getFirst().getFormattedMessage())
          .contains("returned no license certificate");
    }

    @Test
    @DisplayName("Given only the platform_license source should neither entitle nor warn")
    void given_platformLicenseSourceOnly_should_neitherEntitleNorWarn() {
      // Act
      register(answer("ee_enabled", true, "ee_sources", List.of("platform_license")));

      // Assert
      assertThat(service.activeLicense().isPresent()).isFalse();
      assertThat(warnings()).isEmpty();
    }
  }

  @Nested
  @DisplayName("With a certificate")
  class WithCertificate {

    @Test
    @DisplayName("Given a certificate that verifies should entitle")
    void given_verifiedCertificate_should_entitle() {
      // Arrange
      verifierReturns(trialLicenseEndingAt(Instant.now().plus(1, DAYS)));

      // Act
      register(
          answer(
              "ee_enabled", true, "ee_sources", List.of("xtm_sublicense"), "xtm_license_pem", PEM));

      // Assert
      assertThat(service.activeLicense().isPresent()).isTrue();
      verifier.verify(
          () -> XtmLicenseVerifier.verify(eq(PEM), eq(PLATFORM_ID), eq(CREATED_AT), any()));
      assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("Given a certificate that fails verification should warn once with the reason")
    void given_failingCertificate_should_notEntitleAndWarnWithReason() {
      // Arrange
      verifier
          .when(() -> XtmLicenseVerifier.verify(anyString(), anyString(), any(), any()))
          .thenThrow(new XtmLicenseException("its signature does not verify"));

      // Act
      register(answer("ee_enabled", true, "xtm_license_pem", PEM));
      register(answer("ee_enabled", true, "xtm_license_pem", PEM));

      // Assert
      assertThat(service.activeLicense().isPresent()).isFalse();
      assertThat(warnings()).hasSize(1);
      assertThat(warnings().getFirst().getFormattedMessage())
          .contains("its signature does not verify", "not granted");
    }

    @Test
    @DisplayName("Given an unexpected verification error should fail closed")
    void given_verificationError_should_failClosed() {
      // Arrange
      verifier
          .when(() -> XtmLicenseVerifier.verify(anyString(), anyString(), any(), any()))
          .thenThrow(new IllegalStateException("boom"));

      // Act
      register(answer("xtm_license_pem", PEM));

      // Assert
      assertThat(service.activeLicense().isPresent()).isFalse();
      assertThat(warnings()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Heartbeats")
  class Heartbeats {

    @Test
    @DisplayName("Given an answer without a certificate after a grant should end the entitlement")
    void given_answerWithoutCertificateAfterGrant_should_endEntitlement() {
      // Arrange
      verifierReturns(trialLicenseEndingAt(Instant.now().plus(1, DAYS)));
      register(answer("xtm_license_pem", PEM));

      // Act
      register(answer("ee_enabled", true, "ee_sources", List.of()));

      // Assert
      assertThat(service.activeLicense().isPresent()).isFalse();
    }

    @Test
    @DisplayName("Given ee_enabled alone after a grant should end the entitlement")
    void given_eeEnabledAloneAfterGrant_should_endEntitlement() {
      // Arrange
      verifierReturns(trialLicenseEndingAt(Instant.now().plus(1, DAYS)));
      register(answer("xtm_license_pem", PEM));

      // Act
      register(answer("ee_enabled", true));

      // Assert
      assertThat(service.activeLicense().isPresent()).isFalse();
    }

    @Test
    @DisplayName("Given a failing certificate after a grant should end the entitlement")
    void given_failingCertificateAfterGrant_should_endEntitlement() {
      // Arrange
      verifierReturns(trialLicenseEndingAt(Instant.now().plus(1, DAYS)));
      register(answer("xtm_license_pem", PEM));
      verifier
          .when(() -> XtmLicenseVerifier.verify(anyString(), anyString(), any(), any()))
          .thenThrow(new XtmLicenseException("it does not sub-license this platform"));

      // Act
      register(answer("xtm_license_pem", PEM));

      // Assert
      assertThat(service.activeLicense().isPresent()).isFalse();
    }

    @Test
    @DisplayName("Given no answer after a grant should keep the last verified license")
    void given_noAnswerAfterGrant_should_keepLastVerifiedLicense() {
      // Arrange
      verifierReturns(trialLicenseEndingAt(Instant.now().plus(1, DAYS)));
      register(answer("xtm_license_pem", PEM));

      // Act
      register(null);

      // Assert
      assertThat(service.activeLicense().isPresent()).isTrue();
    }

    @Test
    @DisplayName("Given a verified license should lapse at its end between heartbeats")
    void given_verifiedLicense_should_lapseAtItsEndBetweenHeartbeats() {
      // Arrange
      Instant end = Instant.now().plus(1, HOURS);
      verifierReturns(trialLicenseEndingAt(end));

      // Act
      register(answer("xtm_license_pem", PEM));

      // Assert
      assertThat(service.activeLicenseAt(end).isPresent()).isTrue();
      assertThat(service.activeLicenseAt(end.plusSeconds(1)).isPresent()).isFalse();
    }

    @Test
    @DisplayName("Given a grant, a repeat and an end should report a change only on grant and end")
    void given_grantRepeatAndEnd_should_reportChangesOnlyOnTransitions() {
      // Arrange
      verifierReturns(trialLicenseEndingAt(Instant.now().plus(1, DAYS)));

      // Act & Assert
      assertThat(register(answer("xtm_license_pem", PEM))).isTrue();
      assertThat(register(answer("xtm_license_pem", PEM))).isFalse();
      assertThat(register(null)).isFalse();
      assertThat(register(answer("ee_enabled", true, "ee_sources", List.of()))).isTrue();
      assertThat(register(answer("ee_enabled", false, "ee_sources", List.of()))).isFalse();
    }

    @Test
    @DisplayName("Given the same verified license on every heartbeat should log the grant once")
    void given_sameVerifiedLicense_should_logTheGrantOnce() {
      // Arrange
      verifierReturns(trialLicenseEndingAt(Instant.now().plus(1, DAYS)));

      // Act
      register(answer("xtm_license_pem", PEM));
      register(answer("xtm_license_pem", PEM));

      // Assert
      assertThat(
              logs.list.stream()
                  .filter(e -> e.getLevel() == Level.INFO)
                  .map(ILoggingEvent::getFormattedMessage)
                  .filter(message -> message.contains("XTM license verified")))
          .hasSize(1);
      assertThat(warnings()).isEmpty();
    }
  }
}
