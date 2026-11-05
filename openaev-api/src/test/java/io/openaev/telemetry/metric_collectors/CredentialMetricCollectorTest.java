package io.openaev.telemetry.metric_collectors;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.openaev.rest.exception.CredentialResolutionException.ErrorCode;
import io.opentelemetry.api.common.Attributes;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CredentialMetricCollector Tests")
class CredentialMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;

  @InjectMocks private CredentialMetricCollector credentialMetricCollector;

  @Captor private ArgumentCaptor<Supplier<Long>> supplierCaptor;
  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> multiGaugeCaptor;

  private static Attributes usageAttributes(
      String userId, String credentialType, String injectorType) {
    return Attributes.of(
        stringKey("user_hash"), hashWithSHA256(userId),
        stringKey("credential_type"), credentialType,
        stringKey("injector_type"), injectorType);
  }

  private static Attributes errorCodeAttributes(ErrorCode errorCode) {
    return Attributes.of(stringKey("error_code"), errorCode.name());
  }

  @Nested
  @DisplayName("Credential usage gauge")
  class CredentialUsages {

    @Test
    @DisplayName(
        "given credentials attached by users, should count them by hashed user, type and injector")
    void given_attachedCredentials_should_countThemByHashedUserTypeAndInjector() {
      // Arrange
      credentialMetricCollector.addCredentialUsage("user-1", "CLOUD_AWS", "openaev_aws");
      credentialMetricCollector.addCredentialUsage("user-1", "CLOUD_AWS", "openaev_aws");
      credentialMetricCollector.addCredentialUsage("user-2", "CLOUD_AWS", "openaev_aws");

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("credential_usages_count"), anyString(), multiGaugeCaptor.capture());
      assertThat(multiGaugeCaptor.getValue().get())
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  usageAttributes("user-1", "CLOUD_AWS", "openaev_aws"), 2L,
                  usageAttributes("user-2", "CLOUD_AWS", "openaev_aws"), 1L));
    }

    @Test
    @DisplayName("given a user, should export a hash and never the user id")
    void given_user_should_exportHashNotUserId() {
      // Act
      credentialMetricCollector.addCredentialUsage("user-1", "IDENTITY", "openaev_nmap");

      // Assert
      assertThat(credentialMetricCollector.collectCredentialUsages().keySet())
          .singleElement()
          .satisfies(
              attributes -> {
                assertThat(attributes.get(stringKey("user_hash")))
                    .isNotEqualTo("user-1")
                    .isEqualTo(hashWithSHA256("user-1"));
                assertThat(attributes.asMap().values()).doesNotContain("user-1");
              });
    }

    @Test
    @DisplayName("given an inject without injector, should export its injector type as unknown")
    void given_injectWithoutInjector_should_exportUnknownInjectorType() {
      // Act
      credentialMetricCollector.addCredentialUsage("user-1", "CLOUD_AZURE", null);

      // Assert
      assertThat(credentialMetricCollector.collectCredentialUsages())
          .containsExactlyEntriesOf(
              Map.of(usageAttributes("user-1", "CLOUD_AZURE", "unknown"), 1L));
    }

    @Test
    @DisplayName("given collected usages, should reset them for the next export")
    void given_collectedUsages_should_resetForNextExport() {
      // Arrange
      credentialMetricCollector.addCredentialUsage("user-1", "CLOUD_GCP", "openaev_gcp");
      credentialMetricCollector.collectCredentialUsages();

      // Act
      Map<Attributes, Long> nextExport = credentialMetricCollector.collectCredentialUsages();

      // Assert
      assertThat(nextExport).isEmpty();
    }
  }

  @Nested
  @DisplayName("Credential resolution successes gauge")
  class ResolutionSuccesses {

    @Test
    @DisplayName("given successful resolutions, should count them and reset for the next export")
    void given_successfulResolutions_should_countThemAndReset() {
      // Arrange
      credentialMetricCollector.addResolutionSuccess();
      credentialMetricCollector.addResolutionSuccess();

      // Act
      long firstExport = credentialMetricCollector.collectResolutionSuccesses();
      long nextExport = credentialMetricCollector.collectResolutionSuccesses();

      // Assert
      assertThat(firstExport).isEqualTo(2L);
      assertThat(nextExport).isZero();
    }

    @Test
    @DisplayName("given the collector is initialised, should register the successes gauge")
    void given_init_should_registerSuccessesGauge() {
      // Arrange
      credentialMetricCollector.addResolutionSuccess();

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("credential_resolution_successes_count"), anyString(), supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("Credential resolution failures gauge")
  class ResolutionFailures {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("given a refused resolution, should count it under its error code")
    void given_refusedResolution_should_countItsErrorCode(ErrorCode errorCode) {
      // Act
      credentialMetricCollector.addResolutionFailure(errorCode);
      credentialMetricCollector.addResolutionFailure(errorCode);

      // Assert
      assertThat(credentialMetricCollector.collectResolutionFailures())
          .containsExactlyEntriesOf(Map.of(errorCodeAttributes(errorCode), 2L));
    }

    @Test
    @DisplayName("given collected failures, should reset them for the next export")
    void given_collectedFailures_should_resetForNextExport() {
      // Arrange
      credentialMetricCollector.addResolutionFailure(ErrorCode.CREDENTIAL_INACTIVE);
      credentialMetricCollector.collectResolutionFailures();

      // Act
      Map<Attributes, Long> nextExport = credentialMetricCollector.collectResolutionFailures();

      // Assert
      assertThat(nextExport).isEmpty();
    }

    @Test
    @DisplayName("given the collector is initialised, should register the failures gauge")
    void given_init_should_registerFailuresGauge() {
      // Arrange
      credentialMetricCollector.addResolutionFailure(ErrorCode.CREDENTIAL_ACCESS_DENIED);

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("credential_resolution_failures_count"), anyString(), multiGaugeCaptor.capture());
      assertThat(multiGaugeCaptor.getValue().get())
          .containsExactlyEntriesOf(
              Map.of(errorCodeAttributes(ErrorCode.CREDENTIAL_ACCESS_DENIED), 1L));
    }
  }
}
