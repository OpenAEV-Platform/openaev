package io.openaev.telemetry.metric_collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssetMetricCollector Tests")
class AssetMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;
  @Mock private EntityManager entityManager;

  private AssetMetricCollector collector;

  @Captor private ArgumentCaptor<Supplier<Long>> gaugeCaptor;

  @BeforeEach
  void setUp() {
    collector = new AssetMetricCollector(metricRegistry);
    // @PersistenceContext field is container-injected in production
    ReflectionTestUtils.setField(collector, "entityManager", entityManager);
  }

  @SuppressWarnings("unchecked")
  private void mockCount(String jpqlFragment, long count) {
    TypedQuery<Long> query = mock(TypedQuery.class);
    when(query.getSingleResult()).thenReturn(count);
    when(entityManager.createQuery(contains(jpqlFragment), eq(Long.class))).thenReturn(query);
  }

  private Supplier<Long> capturedGauge(String gaugeName) {
    collector.init();
    verify(metricRegistry).registerGauge(eq(gaugeName), any(), gaugeCaptor.capture());
    return gaugeCaptor.getValue();
  }

  @Test
  @DisplayName("total_assets_count reports the number of endpoints, agent based or agentless")
  void given_endpoints_should_reportTotalAssetsCount() {
    mockCount("select count(e) from Endpoint e", 42L);

    assertThat(capturedGauge("total_assets_count").get()).isEqualTo(42L);
  }

  @Test
  @DisplayName("total_agent_based_assets_count reports endpoints with at least one agent")
  void given_agentBasedEndpoints_should_reportAgentBasedAssetsCount() {
    mockCount("e.agents is not empty", 30L);

    assertThat(capturedGauge("total_agent_based_assets_count").get()).isEqualTo(30L);
  }

  @Test
  @DisplayName("total_agentless_assets_count reports endpoints without any agent")
  void given_agentlessEndpoints_should_reportAgentlessAssetsCount() {
    mockCount("where e.agents is empty", 12L);

    assertThat(capturedGauge("total_agentless_assets_count").get()).isEqualTo(12L);
  }

  @Test
  @DisplayName("a query failure reports zero instead of throwing")
  void given_queryFailure_should_reportZero() {
    when(entityManager.createQuery(contains("from Endpoint"), eq(Long.class)))
        .thenThrow(new IllegalStateException("database is down"));

    assertThat(capturedGauge("total_assets_count").get()).isZero();
  }
}
