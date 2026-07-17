package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Map;
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

  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> supplierCaptor;

  @BeforeEach
  void setUp() {
    collector = new AssetMetricCollector(metricRegistry);
    // @PersistenceContext field is container-injected in production
    ReflectionTestUtils.setField(collector, "entityManager", entityManager);
  }

  private void mockRows(List<Object[]> rows) {
    Query query = mock(Query.class);
    when(query.getResultList()).thenReturn(rows);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
  }

  private Supplier<Map<Attributes, Long>> capturedSupplier() {
    collector.init();
    verify(metricRegistry).registerMultiGauge(eq("assets_total"), any(), supplierCaptor.capture());
    return supplierCaptor.getValue();
  }

  private static Attributes attributes(String category, String coverage) {
    return Attributes.of(stringKey("category"), category, stringKey("agent_coverage"), coverage);
  }

  @Test
  @DisplayName("assets are broken down by category and agent coverage")
  void given_assetRows_should_exposeCategoryAndCoverageDimensions() {
    mockRows(
        List.of(
            new Object[] {"HOST", true, 30L},
            new Object[] {"HOST", false, 12L},
            new Object[] {"CLOUD_RESOURCE", false, 5L}));

    Map<Attributes, Long> inventory = capturedSupplier().get();

    assertThat(inventory)
        .containsOnly(
            Map.entry(attributes("HOST", "agent_based"), 30L),
            Map.entry(attributes("HOST", "agentless"), 12L),
            Map.entry(attributes("CLOUD_RESOURCE", "agentless"), 5L));
  }

  @Test
  @DisplayName("a null category is normalized to unknown")
  void given_nullCategory_should_normalizeToUnknown() {
    mockRows(List.<Object[]>of(new Object[] {null, false, 3L}));

    Map<Attributes, Long> inventory = capturedSupplier().get();

    assertThat(inventory).containsOnly(Map.entry(attributes("unknown", "agentless"), 3L));
  }

  @Test
  @DisplayName("a query failure returns an empty inventory instead of throwing")
  void given_queryFailure_should_returnEmptyInventory() {
    when(entityManager.createNativeQuery(anyString()))
        .thenThrow(new IllegalStateException("database is down"));

    Map<Attributes, Long> inventory = capturedSupplier().get();

    assertThat(inventory).isEmpty();
  }
}
