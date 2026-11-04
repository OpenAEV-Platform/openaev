package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.rest.exception.CredentialResolutionException.ErrorCode;
import io.openaev.service.credential.CredentialService;
import io.opentelemetry.api.common.Attributes;
import java.util.List;
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
  @Mock private CredentialService credentialService;

  @InjectMocks private CredentialMetricCollector credentialMetricCollector;

  @Captor private ArgumentCaptor<Supplier<Long>> supplierCaptor;
  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> multiGaugeCaptor;

  private static Attributes credentialAttributes(String type, String authMethod, String status) {
    return Attributes.of(
        stringKey("credential_type"), type,
        stringKey("auth_method"), authMethod,
        stringKey("status"), status);
  }

  private static Attributes errorCodeAttributes(ErrorCode errorCode) {
    return Attributes.of(stringKey("error_code"), errorCode.name());
  }

  private static Attributes usageAttributes(String credentialType, String injectorType) {
    return Attributes.of(
        stringKey("credential_type"), credentialType, stringKey("injector_type"), injectorType);
  }

  @Nested
  @DisplayName("Credential count gauge")
  class CredentialCount {

    @Test
    @DisplayName("given credentials, should report the total count")
    void given_credentials_should_returnCorrectCount() {
      // Arrange
      when(credentialService.globalCount()).thenReturn(2L);

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("total_credentials_count"), eq("Number of credentials"), supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isEqualTo(2L);
    }
  }

  @Nested
  @DisplayName("Credential breakdown gauge")
  class CredentialBreakdown {

    @Test
    @DisplayName("given credential rows, should expose type, auth method and status dimensions")
    void given_credentialRows_should_exposeTypeAuthMethodStatusDimensions() {
      // Arrange
      when(credentialService.globalCountByTypeAuthMethodAndStatus())
          .thenReturn(
              List.of(
                  new Object[] {"CLOUD_AWS", "AWS_ACCESS_KEY", "ACTIVE", 3L},
                  new Object[] {"IDENTITY", "USERNAME_PASSWORD", "UNSET", 2L}));

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("credentials_total"), anyString(), multiGaugeCaptor.capture());
      assertThat(multiGaugeCaptor.getValue().get())
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  credentialAttributes("CLOUD_AWS", "AWS_ACCESS_KEY", "ACTIVE"), 3L,
                  credentialAttributes("IDENTITY", "USERNAME_PASSWORD", "UNSET"), 2L));
    }

    @Test
    @DisplayName("given a null column, should export it as unknown")
    void given_nullColumn_should_exportUnknownLabel() {
      // Arrange
      when(credentialService.globalCountByTypeAuthMethodAndStatus())
          .thenReturn(List.<Object[]>of(new Object[] {"CLOUD_GCP", "GCP_OAUTH2", null, 1L}));

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("credentials_total"), anyString(), multiGaugeCaptor.capture());
      assertThat(multiGaugeCaptor.getValue().get())
          .containsExactlyEntriesOf(
              Map.of(credentialAttributes("CLOUD_GCP", "GCP_OAUTH2", "unknown"), 1L));
    }

    @Test
    @DisplayName("given a failing query, should report an empty breakdown instead of throwing")
    void given_failingQuery_should_returnEmptyBreakdown() {
      // Arrange
      when(credentialService.globalCountByTypeAuthMethodAndStatus())
          .thenThrow(new IllegalStateException("database down"));

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(eq("credentials_total"), anyString(), multiGaugeCaptor.capture());
      assertThat(multiGaugeCaptor.getValue().get()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Injects with credentials gauge")
  class InjectsWithCredentials {

    @Test
    @DisplayName("given injects using credentials, should report their count")
    void given_injectsWithCredentials_should_returnCount() {
      // Arrange
      when(credentialService.globalCountInjectsWithCredentials()).thenReturn(5L);

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("injects_with_credentials_total"), anyString(), supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isEqualTo(5L);
    }

    @Test
    @DisplayName("given a failing query, should report zero instead of throwing")
    void given_failingQuery_should_returnZero() {
      // Arrange
      when(credentialService.globalCountInjectsWithCredentials())
          .thenThrow(new IllegalStateException("database down"));

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("injects_with_credentials_total"), anyString(), supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isZero();
    }
  }

  @Nested
  @DisplayName("Credentials attached to injects gauge")
  class CredentialsAttachedToInjects {

    @Test
    @DisplayName("given credentials attached to injects, should report their count")
    void given_credentialsAttachedToInjects_should_returnCount() {
      // Arrange
      when(credentialService.globalCountCredentialsAttachedToInjects()).thenReturn(4L);

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("credentials_attached_to_injects_total"), anyString(), supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isEqualTo(4L);
    }

    @Test
    @DisplayName("given a failing query, should report zero instead of throwing")
    void given_failingQuery_should_returnZero() {
      // Arrange
      when(credentialService.globalCountCredentialsAttachedToInjects())
          .thenThrow(new IllegalStateException("database down"));

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("credentials_attached_to_injects_total"), anyString(), supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isZero();
    }
  }

  @Nested
  @DisplayName("Users with injects using credentials gauge")
  class UsersWithInjectsUsingCredentials {

    @Test
    @DisplayName("given users owning injects with credentials, should report their count")
    void given_usersWithInjectsUsingCredentials_should_returnCount() {
      // Arrange
      when(credentialService.globalCountUsersWithInjectsUsingCredentials()).thenReturn(3L);

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("users_with_injects_using_credentials_total"),
              anyString(),
              supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isEqualTo(3L);
    }

    @Test
    @DisplayName("given a failing query, should report zero instead of throwing")
    void given_failingQuery_should_returnZero() {
      // Arrange
      when(credentialService.globalCountUsersWithInjectsUsingCredentials())
          .thenThrow(new IllegalStateException("database down"));

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("users_with_injects_using_credentials_total"),
              anyString(),
              supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isZero();
    }
  }

  @Nested
  @DisplayName("Credential usage gauge")
  class CredentialUsages {

    @Test
    @DisplayName("given usage rows, should expose credential type and injector type dimensions")
    void given_usageRows_should_exposeCredentialTypeAndInjectorTypeDimensions() {
      // Arrange
      when(credentialService.globalCountUsagesByCredentialTypeAndInjectorType())
          .thenReturn(
              List.of(
                  new Object[] {"CLOUD_AWS", "openaev_aws", 6L},
                  new Object[] {"IDENTITY", "openaev_nmap", 1L}));

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("credential_usages_total"), anyString(), multiGaugeCaptor.capture());
      assertThat(multiGaugeCaptor.getValue().get())
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  usageAttributes("CLOUD_AWS", "openaev_aws"), 6L,
                  usageAttributes("IDENTITY", "openaev_nmap"), 1L));
    }

    @Test
    @DisplayName("given an uninstalled injector, should export its type as unknown")
    void given_uninstalledInjector_should_exportUnknownInjectorType() {
      // Arrange
      when(credentialService.globalCountUsagesByCredentialTypeAndInjectorType())
          .thenReturn(List.<Object[]>of(new Object[] {"CLOUD_AZURE", null, 2L}));

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("credential_usages_total"), anyString(), multiGaugeCaptor.capture());
      assertThat(multiGaugeCaptor.getValue().get())
          .containsExactlyEntriesOf(Map.of(usageAttributes("CLOUD_AZURE", "unknown"), 2L));
    }

    @Test
    @DisplayName("given a failing query, should report an empty breakdown instead of throwing")
    void given_failingQuery_should_returnEmptyBreakdown() {
      // Arrange
      when(credentialService.globalCountUsagesByCredentialTypeAndInjectorType())
          .thenThrow(new IllegalStateException("database down"));

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("credential_usages_total"), anyString(), multiGaugeCaptor.capture());
      assertThat(multiGaugeCaptor.getValue().get()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Credential resolution failures gauge")
  class CredentialResolutionFailures {

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
