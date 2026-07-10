package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.opentelemetry.api.common.Attributes;
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
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformAdoptionMetricCollector Tests")
class PlatformAdoptionMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;
  @Mock private Environment environment;
  @Mock private TenantXtmHubRegistrationRepository tenantXtmHubRegistrationRepository;

  @InjectMocks private PlatformAdoptionMetricCollector collector;

  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> multiGaugeCaptor;
  @Captor private ArgumentCaptor<Supplier<Long>> gaugeCaptor;

  @Nested
  @DisplayName("SSO strategies")
  class SsoStrategies {

    @Test
    @DisplayName("every strategy maps to its exact configuration property key")
    void given_enabledAuthProperties_should_reportEachStrategyFromItsExactPropertyKey() {
      // Only the exact expected keys return true: a typo in the collector's
      // property mapping would surface here as a strategy stuck at 0.
      when(environment.getProperty("openaev.auth-local-enable", "false")).thenReturn("true");
      when(environment.getProperty("openaev.auth-openid-enable", "false")).thenReturn("false");
      when(environment.getProperty("openaev.auth-saml2-enable", "false")).thenReturn("true");
      when(environment.getProperty("openaev.auth-kerberos-enable", "false")).thenReturn("false");

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(
              eq("sso_strategy_enabled"), any(), multiGaugeCaptor.capture(), eq("boolean"));
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot)
          .containsOnly(
              Map.entry(Attributes.of(stringKey("strategy"), "local"), 1L),
              Map.entry(Attributes.of(stringKey("strategy"), "oidc"), 0L),
              Map.entry(Attributes.of(stringKey("strategy"), "saml2"), 1L),
              Map.entry(Attributes.of(stringKey("strategy"), "kerberos"), 0L));
    }
  }

  @Nested
  @DisplayName("XTM Hub registration")
  class XtmHubRegistration {

    @Test
    @DisplayName("reports 1 when at least one tenant registration exists")
    void given_existingRegistration_should_reportRegistered() {
      when(tenantXtmHubRegistrationRepository.count()).thenReturn(2L);

      collector.init();

      verify(metricRegistry)
          .registerGauge(eq("xtm_hub_registered"), any(), gaugeCaptor.capture(), eq("boolean"));
      assertThat(gaugeCaptor.getValue().get()).isEqualTo(1L);
    }

    @Test
    @DisplayName("reports 0 when no registration exists or the lookup fails")
    void given_noRegistrationOrFailure_should_reportZero() {
      when(tenantXtmHubRegistrationRepository.count())
          .thenReturn(0L)
          .thenThrow(new RuntimeException("db unavailable"));

      collector.init();

      verify(metricRegistry)
          .registerGauge(eq("xtm_hub_registered"), any(), gaugeCaptor.capture(), eq("boolean"));
      assertThat(gaugeCaptor.getValue().get()).isZero();
      // Repository failure degrades to 0 instead of breaking the telemetry export
      assertThat(gaugeCaptor.getValue().get()).isZero();
    }
  }
}
