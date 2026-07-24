package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.repository.ArticleRepository;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.ChannelRepository;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.database.repository.CveRepository;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.NotificationTriggerRepository;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.ReportRepository;
import io.openaev.database.repository.VulnerabilityRepository;
import io.openaev.database.repository.VulnerableEndpointRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.opentelemetry.api.common.Attributes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductInventoryMetricCollector Tests")
class ProductInventoryMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;
  @Mock private AssetGroupRepository assetGroupRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private ChallengeRepository challengeRepository;
  @Mock private DocumentRepository documentRepository;
  @Mock private ChannelRepository channelRepository;
  @Mock private ArticleRepository articleRepository;
  @Mock private CustomDashboardRepository customDashboardRepository;
  @Mock private ImportMapperRepository importMapperRepository;
  @Mock private NotificationTriggerRepository notificationTriggerRepository;
  @Mock private WorkflowRepository workflowRepository;
  @Mock private FindingRepository findingRepository;
  @Mock private VulnerabilityRepository vulnerabilityRepository;
  @Mock private CveRepository cveRepository;
  @Mock private VulnerableEndpointRepository vulnerableEndpointRepository;
  @Mock private AttackPatternRepository attackPatternRepository;
  @Mock private ReportRepository reportRepository;
  @Mock private EntityManager entityManager;

  private ProductInventoryMetricCollector collector;

  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> multiGaugeCaptor;
  @Captor private ArgumentCaptor<Supplier<Long>> gaugeCaptor;

  @BeforeEach
  void setUp() {
    collector =
        new ProductInventoryMetricCollector(
            metricRegistry,
            assetGroupRepository,
            organizationRepository,
            injectRepository,
            challengeRepository,
            documentRepository,
            channelRepository,
            articleRepository,
            customDashboardRepository,
            importMapperRepository,
            notificationTriggerRepository,
            workflowRepository,
            findingRepository,
            vulnerabilityRepository,
            cveRepository,
            vulnerableEndpointRepository,
            attackPatternRepository,
            reportRepository);
    // @PersistenceContext field is container-injected in production
    ReflectionTestUtils.setField(collector, "entityManager", entityManager);
  }

  @SuppressWarnings("unchecked")
  private TypedQuery<Object[]> mockQuery(String jpqlFragment, List<Object[]> rows) {
    TypedQuery<Object[]> query = mock(TypedQuery.class);
    when(query.getResultList()).thenReturn(rows);
    when(entityManager.createQuery(contains(jpqlFragment), eq(Object[].class))).thenReturn(query);
    return query;
  }

  @Nested
  @DisplayName("Dimensioned inventory gauges")
  class DimensionedGauges {

    @Test
    @DisplayName("payload rows group into type/source/status attributes with unknown for nulls")
    void given_payloadRows_should_exposeTypeSourceStatusDimensions() {
      mockQuery(
          "from Payload",
          List.of(
              new Object[] {"Command", "MANUAL", "VERIFIED", 3L},
              // NULL columns must export as "unknown", never as the literal "null"
              new Object[] {"Executable", null, "UNVERIFIED", 1L}));

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(eq("payloads_total"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot)
          .containsEntry(
              Attributes.of(
                  stringKey("type"), "Command",
                  stringKey("source"), "MANUAL",
                  stringKey("status"), "VERIFIED"),
              3L)
          .containsEntry(
              Attributes.of(
                  stringKey("type"), "Executable",
                  stringKey("source"), "unknown",
                  stringKey("status"), "UNVERIFIED"),
              1L);
    }

    @Test
    @DisplayName("team rows map the contextual flag to a boolean dimension")
    void given_teamRows_should_exposeContextualDimension() {
      mockQuery("from Team", List.of(new Object[] {Boolean.TRUE, 4L}, new Object[] {null, 6L}));

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(eq("teams_total"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot)
          .containsEntry(Attributes.of(booleanKey("contextual"), true), 4L)
          .containsEntry(Attributes.of(booleanKey("contextual"), false), 6L);
    }

    @Test
    @DisplayName("security platforms are dimensioned by platform type")
    void given_securityPlatformRows_should_exposeTypeDimension() {
      mockQuery(
          "from SecurityPlatform", List.of(new Object[] {"EDR", 2L}, new Object[] {"SIEM", 1L}));

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(eq("security_platforms_total"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot)
          .containsEntry(Attributes.of(stringKey("type"), "EDR"), 2L)
          .containsEntry(Attributes.of(stringKey("type"), "SIEM"), 1L);
    }

    @Test
    @DisplayName("null endpoint platforms fall back to unknown")
    void given_nullPlatformRow_should_fallBackToUnknown() {
      mockQuery("from Endpoint", List.<Object[]>of(new Object[] {null, 2L}));

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(eq("endpoints_total"), any(), multiGaugeCaptor.capture());
      Map<Attributes, Long> snapshot = multiGaugeCaptor.getValue().get();
      assertThat(snapshot).containsEntry(Attributes.of(stringKey("platform"), "unknown"), 2L);
    }

    @Test
    @DisplayName("query failures degrade to an empty snapshot instead of breaking the export")
    void given_failingQuery_should_returnEmptySnapshot() {
      when(entityManager.createQuery(anyString(), eq(Object[].class)))
          .thenThrow(new RuntimeException("db unavailable"));

      collector.init();

      verify(metricRegistry)
          .registerMultiGauge(eq("payloads_total"), any(), multiGaugeCaptor.capture());
      assertThat(multiGaugeCaptor.getValue().get()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Scalar inventory gauges")
  class ScalarGauges {

    @Test
    @DisplayName("repository counts are exported as-is")
    void given_repositoryCount_should_exposeCount() {
      when(assetGroupRepository.count()).thenReturn(7L);

      collector.init();

      verify(metricRegistry).registerGauge(eq("asset_groups_total"), any(), gaugeCaptor.capture());
      assertThat(gaugeCaptor.getValue().get()).isEqualTo(7L);
    }

    @Test
    @DisplayName("count failures degrade to 0 instead of breaking the export")
    void given_failingCount_should_fallBackToZero() {
      when(findingRepository.count()).thenThrow(new RuntimeException("db unavailable"));

      collector.init();

      verify(metricRegistry).registerGauge(eq("findings_total"), any(), gaugeCaptor.capture());
      assertThat(gaugeCaptor.getValue().get()).isZero();
    }
  }
}
