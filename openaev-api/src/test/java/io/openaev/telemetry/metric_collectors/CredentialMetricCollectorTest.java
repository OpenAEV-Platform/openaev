package io.openaev.telemetry.metric_collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.service.credential.CredentialService;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;
  @Mock private CredentialService credentialService;

  @InjectMocks private CredentialMetricCollector credentialMetricCollector;

  @Captor private ArgumentCaptor<Supplier<Long>> supplierCaptor;

  @Test
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
