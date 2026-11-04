package io.openaev.telemetry.metric_collectors;

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
@DisplayName("CredentialResolutionMetricCollector Tests")
class CredentialResolutionMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;

  @InjectMocks private CredentialResolutionMetricCollector collector;

  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> multiGaugeCaptor;
  @Captor private ArgumentCaptor<Supplier<Long>> supplierCaptor;

  private static Attributes errorCodeAttributes(ErrorCode errorCode) {
    return Attributes.of(stringKey("error_code"), errorCode.name());
  }

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  @DisplayName("given a refused resolution, should count it under its error code")
  void given_refusedResolution_should_countItsErrorCode(ErrorCode errorCode) {
    // Act
    collector.addResolutionFailure(errorCode);
    collector.addResolutionFailure(errorCode);

    // Assert
    assertThat(collector.collectResolutionFailures())
        .containsExactlyEntriesOf(Map.of(errorCodeAttributes(errorCode), 2L));
  }

  @Test
  @DisplayName("given collected failures, should reset them for the next export")
  void given_collectedFailures_should_resetForNextExport() {
    // Arrange
    collector.addResolutionFailure(ErrorCode.CREDENTIAL_INACTIVE);
    collector.collectResolutionFailures();

    // Act
    Map<Attributes, Long> nextExport = collector.collectResolutionFailures();

    // Assert
    assertThat(nextExport).isEmpty();
  }

  @Test
  @DisplayName("given the collector is initialised, should register the failures gauge")
  void given_init_should_registerFailuresGauge() {
    // Arrange
    collector.addResolutionFailure(ErrorCode.CREDENTIAL_ACCESS_DENIED);

    // Act
    collector.init();

    // Assert
    verify(metricRegistry)
        .registerMultiGauge(
            eq("credential_resolution_failures_count"), anyString(), multiGaugeCaptor.capture());
    assertThat(multiGaugeCaptor.getValue().get())
        .containsExactlyEntriesOf(
            Map.of(errorCodeAttributes(ErrorCode.CREDENTIAL_ACCESS_DENIED), 1L));
  }

  @Test
  @DisplayName("given successful resolutions, should count them and reset for the next export")
  void given_successfulResolutions_should_countThemAndReset() {
    // Arrange
    collector.addResolutionSuccess();
    collector.addResolutionSuccess();

    // Act
    long firstExport = collector.collectResolutionSuccesses();
    long nextExport = collector.collectResolutionSuccesses();

    // Assert
    assertThat(firstExport).isEqualTo(2L);
    assertThat(nextExport).isZero();
  }

  @Test
  @DisplayName("given the collector is initialised, should register the successes gauge")
  void given_init_should_registerSuccessesGauge() {
    // Arrange
    collector.addResolutionSuccess();

    // Act
    collector.init();

    // Assert
    verify(metricRegistry)
        .registerGauge(
            eq("credential_resolution_successes_count"), anyString(), supplierCaptor.capture());
    assertThat(supplierCaptor.getValue().get()).isEqualTo(1L);
  }
}
