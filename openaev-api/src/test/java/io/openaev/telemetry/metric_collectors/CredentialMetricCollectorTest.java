package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.service.credential.CredentialService;
import io.opentelemetry.api.common.Attributes;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
