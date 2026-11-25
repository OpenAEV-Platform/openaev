package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.openaev.IntegrationTest;
import io.openaev.annotation.EsQueryable;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Filters;
import io.openaev.database.raw.RawGrant;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.engine.api.CountRuntime;
import io.openaev.engine.api.FlatConfiguration;
import io.openaev.engine.api.StructuralHistogramWidget;
import io.openaev.engine.api.WidgetConfigurationWithSeries.Series;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.query.EsSeries;
import io.openaev.engine.query.EsSeriesData;
import io.openaev.schema.PropertySchema;
import io.openaev.schema.SchemaUtils;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regression tests for #9873: a Boolean engine field declared {@code @EsQueryable(keyword = true)}
 * was queried on a {@code .keyword} subfield the drivers never map for booleans, so every filter,
 * sort and term histogram on it silently matched nothing. Runs against whichever engine the CI
 * shard starts (Elasticsearch 8, 9 or OpenSearch).
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("Analytics on boolean asset fields (#9873)")
class BooleanFieldAnalyticsIntegrationTest extends IntegrationTest {

  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private EndpointComposer endpointComposer;

  private static final String ADMIN_USER_ID = "test-admin-" + UUID.randomUUID();

  private static final RawUserAuth ADMIN_USER =
      new RawUserAuth() {
        @Override
        public String getUser_id() {
          return ADMIN_USER_ID;
        }

        @Override
        public boolean getUser_admin() {
          return true;
        }

        @Override
        public Set<RawGrant> getUser_grants() {
          return Set.of();
        }
      };

  @BeforeEach
  void resetIndexAndComposers() throws IOException {
    endpointComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }
    entityManager.createNativeQuery("DELETE FROM indexing_status").executeUpdate();
    entityManager.flush();
    entityManager.clear();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Two hosts: the first internet facing and end of life, the second neither. */
  private void indexTwoHosts() {
    Endpoint exposed = EndpointFixture.createEndpoint("exposed-host");
    exposed.setInternetFacing(true);
    exposed.setEoL(true);
    Endpoint internal = EndpointFixture.createEndpoint("internal-host");
    internal.setInternetFacing(false);
    internal.setEoL(false);
    endpointComposer.forEndpoint(exposed).persist();
    endpointComposer.forEndpoint(internal).persist();
    entityManager.flush();
    entityManager.clear();
    engineService.bulkProcessing(engineContext.getModels().stream());
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(() -> assertThat(countAssets(List.of())).isEqualTo(2));
  }

  private static Filters.Filter filter(
      String key, Filters.FilterOperator operator, List<String> values) {
    Filters.Filter filter = new Filters.Filter();
    filter.setId(UUID.randomUUID().toString());
    filter.setKey(key);
    filter.setMode(Filters.FilterMode.or);
    filter.setOperator(operator);
    filter.setValues(values);
    return filter;
  }

  private static Series assetSeries(List<Filters.Filter> extraFilters) {
    List<Filters.Filter> filters = new ArrayList<>();
    filters.add(filter("base_entity", Filters.FilterOperator.eq, List.of("asset")));
    filters.addAll(extraFilters);
    Filters.FilterGroup group = Filters.FilterGroup.defaultFilterGroup();
    group.setFilters(filters);
    Series series = new Series();
    series.setName("assets");
    series.setFilter(group);
    return series;
  }

  private long countAssets(List<Filters.Filter> extraFilters) {
    FlatConfiguration config = new FlatConfiguration();
    config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    config.setSeries(List.of(assetSeries(extraFilters)));
    return engineService
        .count(TxCtx.allTenants(), ADMIN_USER, new CountRuntime(config, Map.of(), Map.of()))
        .getIntervalCount();
  }

  private Map<String, Long> termHistogram(String field) {
    StructuralHistogramWidget widget = new StructuralHistogramWidget();
    widget.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    widget.setField(field);
    widget.setDateAttribute("base_updated_at");
    Series series = assetSeries(List.of());
    widget.setSeries(List.of(series));
    EsSeries result =
        engineService.termHistogram(
            TxCtx.allTenants(), ADMIN_USER, widget, series, Map.of(), Map.of());
    return result.getData().stream()
        .collect(Collectors.toMap(EsSeriesData::getKey, EsSeriesData::getValue));
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Filters on a boolean field target the raw boolean field")
  class Filtering {

    @ParameterizedTest
    @ValueSource(strings = {"asset_internet_facing", "endpoint_is_eol"})
    void given_twoHosts_should_countEachBooleanValue(String field) {
      indexTwoHosts();

      assertThat(countAssets(List.of(filter(field, Filters.FilterOperator.eq, List.of("true")))))
          .as("%s eq true", field)
          .isEqualTo(1);
      assertThat(countAssets(List.of(filter(field, Filters.FilterOperator.eq, List.of("false")))))
          .as("%s eq false", field)
          .isEqualTo(1);
      assertThat(
              countAssets(List.of(filter(field, Filters.FilterOperator.not_eq, List.of("true")))))
          .as("%s not_eq true", field)
          .isEqualTo(1);
      assertThat(countAssets(List.of(filter(field, Filters.FilterOperator.not_empty, List.of()))))
          .as("%s not_empty", field)
          .isEqualTo(2);
      assertThat(countAssets(List.of(filter(field, Filters.FilterOperator.empty, List.of()))))
          .as("%s empty", field)
          .isZero();
    }
  }

  @Nested
  @DisplayName("A term histogram on a boolean field has one bucket per value")
  class Histogram {

    @ParameterizedTest
    @ValueSource(strings = {"asset_internet_facing", "endpoint_is_eol"})
    void given_twoHosts_should_bucketEachBooleanValue(String field) {
      indexTwoHosts();

      Map<String, Long> buckets = termHistogram(field);

      assertThat(buckets).as("%s buckets", field).hasSize(2);
      assertThat(buckets.values()).as("%s bucket counts", field).containsOnly(1L);
    }
  }

  @Test
  @DisplayName("No indexed engine field declares keyword unless it holds strings")
  void given_engineModels_should_declareKeywordOnStringFieldsOnly() {
    List<String> offenders = new ArrayList<>();
    for (EsModel<?> model : engineContext.getModels()) {
      for (Class<?> clazz = model.getModel();
          clazz != null && clazz != Object.class;
          clazz = clazz.getSuperclass()) {
        Map<String, PropertySchema> schemas =
            SchemaUtils.schema(clazz).stream()
                .collect(Collectors.toMap(PropertySchema::getName, p -> p, (a, b) -> a));
        for (Field field : clazz.getDeclaredFields()) {
          EsQueryable esQueryable = field.getAnnotation(EsQueryable.class);
          PropertySchema schema = schemas.get(field.getName());
          if (esQueryable != null
              && esQueryable.keyword()
              && schema != null
              && !schema.isStringValued()) {
            offenders.add(clazz.getSimpleName() + "." + field.getName());
          }
        }
      }
    }
    assertThat(offenders)
        .as(
            "keyword = true on a non-string field targets a `.keyword` subfield the drivers never"
                + " map (only String and collections of String get one)")
        .isEmpty();
  }

}
