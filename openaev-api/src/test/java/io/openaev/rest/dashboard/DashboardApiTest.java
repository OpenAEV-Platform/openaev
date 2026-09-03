package io.openaev.rest.dashboard;

import static io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType.timeRange;
import static io.openaev.rest.dashboard.DashboardApi.DASHBOARD_URI;
import static io.openaev.utils.CustomDashboardTimeRange.*;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.engine.EngineContext;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.EsModel;
import io.openaev.engine.api.EngineSortField;
import io.openaev.engine.api.HistogramInterval;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.SortDirection;
import io.openaev.rest.custom_dashboard.form.CustomDashboardInput;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.es.EntitiesPaginationInput;
import io.openaev.utils.es.WidgetToEntitiesInput;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.Pagination;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Dashboard API tests")
class DashboardApiTest extends IntegrationTest {

  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private WidgetComposer widgetComposer;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private CustomDashboardParameterComposer customDashboardParameterComposer;
  @Autowired private AttackPatternRepository attackPatternRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  @BeforeEach
  void setup() throws IOException {
    endpointComposer.reset();
    widgetComposer.reset();
    exerciseComposer.reset();
    injectComposer.reset();

    // force reset elastic
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }
  }

  @Nested
  @DisplayName("When fetching entities from dimension")
  class WhenFetchingEntitiesFromDimension {

    @Test
    @DisplayName("When no specific filter, return all entities from dimension.")
    void WhenNoSpecificFilter_ReturnAllEntitiesFromDimension() throws Exception {
      Endpoint ep = endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();
      Widget widget =
          widgetComposer
              .forWidget(WidgetFixture.createListWidgetWithEntity("asset"))
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      // force persistence
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async, so the method above
      // completes before the data is available in the system
      Thread.sleep(1000);

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/entities/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("es_datas[0].base_id").isEqualTo(ep.getId());
      assertThatJson(response).node("total").isEqualTo(1);
    }

    @Test
    @DisplayName("When sorting is specified, return entities sorted accordingly.")
    void WhenSortingIsSpecified_ReturnEntitiesSortedAccordingly() throws Exception {
      // some endpoints
      EndpointComposer.Composer epWrapper3 =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      epWrapper3.get().setHostname("ep3");
      epWrapper3.persist();
      EndpointComposer.Composer epWrapper1 =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      epWrapper1.get().setHostname("ep1");
      epWrapper1.persist();
      EndpointComposer.Composer epWrapper2 =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      epWrapper2.get().setHostname("ep2");
      epWrapper2.persist();

      Widget listWidget = WidgetFixture.createListWidgetWithEntity("asset");
      EngineSortField sortField = new EngineSortField();
      sortField.setFieldName("asset_hostname");
      sortField.setDirection(SortDirection.ASC);
      ((ListConfiguration) listWidget.getWidgetConfiguration()).setSorts(List.of(sortField));
      Widget widget =
          widgetComposer
              .forWidget(listWidget)
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      // force persistence
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async, so the method above
      // completes before the data is available in the system
      Thread.sleep(1000);

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/entities/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("total").isEqualTo(3);
      assertThatJson(response).node("es_datas[0].base_id").isEqualTo(epWrapper1.get().getId());
      assertThatJson(response).node("es_datas[1].base_id").isEqualTo(epWrapper2.get().getId());
      assertThatJson(response).node("es_datas[2].base_id").isEqualTo(epWrapper3.get().getId());
    }

    @Test
    @DisplayName("When binding with dashboard parameter, param is applied to returned collection.")
    void WhenBindingWithDashboardParam_ParamIsAppliedToReturnedCollection() throws Exception {
      // some endpoints
      EndpointComposer.Composer epWrapper3 =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      epWrapper3.get().setHostname("ep3");
      EndpointComposer.Composer epWrapper1 =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      epWrapper1.get().setHostname("ep1");
      EndpointComposer.Composer epWrapper2 =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      epWrapper2.get().setHostname("ep2");

      // single simulation with two findings
      // each referencing the same two endpoints
      ExerciseComposer.Composer exerciseWrapper1 =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(
                  injectComposer
                      .forInject(InjectFixture.getDefaultInject())
                      .withFinding(
                          findingComposer
                              .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
                              .withEndpoint(epWrapper1)
                              .withEndpoint(epWrapper2))
                      .withFinding(
                          findingComposer
                              .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
                              .withEndpoint(epWrapper1)
                              .withEndpoint(epWrapper2)))
              .persist();

      // other simulation with single finding referencing another endpoint
      exerciseComposer
          .forExercise(ExerciseFixture.createDefaultExercise())
          .withInject(
              injectComposer
                  .forInject(InjectFixture.getDefaultInject())
                  .withFinding(
                      findingComposer
                          .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
                          .withEndpoint(epWrapper3)))
          .persist();

      CustomDashboardParameterComposer.Composer paramWrapper =
          customDashboardParameterComposer.forCustomDashboardParameter(
              CustomDashboardParameterFixture.createSimulationCustomDashboardParameter());
      CustomDashboardComposer.Composer dashboardWrapper =
          customDashboardComposer
              .forCustomDashboard(CustomDashboardFixture.createCustomDashboardWithDefaultParams())
              .withCustomDashboardParameter(paramWrapper)
              .persist();

      Widget listWidget = WidgetFixture.createListWidgetWithEntity("vulnerable-endpoint");
      ListConfiguration config = (ListConfiguration) listWidget.getWidgetConfiguration();
      // filters
      Filters.FilterGroup filterGroup = config.getPerspective().getFilter();
      Filters.Filter simulationFilter = new Filters.Filter();
      simulationFilter.setKey("base_simulation_side");
      simulationFilter.setMode(Filters.FilterMode.or);
      simulationFilter.setOperator(Filters.FilterOperator.eq);
      simulationFilter.setValues(List.of(paramWrapper.get().getId()));
      List<Filters.Filter> filters = new ArrayList<>(filterGroup.getFilters());
      filters.add(simulationFilter);
      filterGroup.setFilters(filters);

      // sorts
      EngineSortField sortField = new EngineSortField();
      sortField.setFieldName("vulnerable_endpoint_hostname");
      sortField.setDirection(SortDirection.DESC);
      config.setSorts(List.of(sortField));
      Widget widget =
          widgetComposer
              .forWidget(listWidget)
              .withCustomDashboard(dashboardWrapper)
              .persist()
              .get();

      // force persistence
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async, so the method above
      // completes before the data is available in the system
      Thread.sleep(1000);

      EntitiesPaginationInput input = new EntitiesPaginationInput();
      Map<String, String> params = new HashMap<>();
      params.put(paramWrapper.get().getId(), exerciseWrapper1.get().getId());
      input.setParameters(params);
      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/entities/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .node("es_datas[0].vulnerable_endpoint_id")
          .isEqualTo(epWrapper2.get().getId());
      assertThatJson(response)
          .node("es_datas[1].vulnerable_endpoint_id")
          .isEqualTo(epWrapper1.get().getId());
      assertThatJson(response).node("es_datas").isArray().size().isEqualTo(2);
      assertThatJson(response).node("total").isEqualTo(2);
    }

    @Test
    @DisplayName("When paginating is specified, return entities paginated accordingly")
    void WhenPaginatingIsSpecified_ReturnEntitiesPaginatedAccordingly() throws Exception {
      endpointComposer.forEndpoint(EndpointFixture.createEndpoint("A")).persist().get();
      endpointComposer.forEndpoint(EndpointFixture.createEndpoint("B")).persist().get();
      Endpoint epC =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint("C")).persist().get();
      Endpoint epD =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint("D")).persist().get();
      endpointComposer.forEndpoint(EndpointFixture.createEndpoint("E")).persist().get();

      Widget listWidget = WidgetFixture.createListWidgetWithEntity("asset");
      EngineSortField sortField = new EngineSortField();
      sortField.setFieldName("asset_name");
      sortField.setDirection(SortDirection.ASC);
      ((ListConfiguration) listWidget.getWidgetConfiguration()).setSorts(List.of(sortField));
      Widget widget =
          widgetComposer
              .forWidget(listWidget)
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      // force persistence
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async, so the method above
      // completes before the data is available in the system
      Thread.sleep(1000);

      EntitiesPaginationInput input = new EntitiesPaginationInput();
      Pagination pagination = new Pagination();
      pagination.setPage(1);
      pagination.setSize(2);
      input.setPagination(pagination);

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/entities/" + widget.getId())
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("total").isEqualTo(5);
      assertThatJson(response).node("page_number").isEqualTo(1);
      assertThatJson(response).node("page_size").isEqualTo(2);
      assertThatJson(response).node("total_pages").isEqualTo(3);
      assertThatJson(response).node("es_datas[0].base_id").isEqualTo(epC.getId());
      assertThatJson(response).node("es_datas[1].base_id").isEqualTo(epD.getId());
    }
  }

  @Nested
  @DisplayName("When fetching entities to count")
  class WhenFetchingEntitiesToCount {

    @Test
    @DisplayName("Count all entities with no specific filter.")
    void countAllEntitiesWithNoSpecificFilter() throws Exception {
      endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      Widget widget =
          widgetComposer
              .forWidget(WidgetFixture.createNumberWidgetWithEntity("asset"))
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      // force persistence
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async, so the method above
      // completes before the data is available in the system
      Thread.sleep(1000);

      List<CustomDashboardParameters> parameters = widget.getCustomDashboard().getParameters();
      String timeRangeParameterId =
          parameters.stream()
              .filter(param -> param.getType() == timeRange)
              .findFirst()
              .orElseThrow()
              .getId();

      Map<String, String> input = new HashMap<>();
      input.put(timeRangeParameterId, String.valueOf(LAST_QUARTER));

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/count/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("interval_count").isEqualTo(3);
      assertThatJson(response).node("previous_interval_count").isEqualTo(0);
      assertThatJson(response).node("difference_count").isEqualTo(3);
    }

    @Test
    @DisplayName("Count no entity with no specific filter.")
    void countNoEntityWithNoSpecificFilter() throws Exception {
      Widget widget =
          widgetComposer
              .forWidget(WidgetFixture.createNumberWidgetWithEntity("asset"))
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      // force persistence
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async, so the method above
      // completes before the data is available in the system
      Thread.sleep(1000);

      List<CustomDashboardParameters> parameters = widget.getCustomDashboard().getParameters();
      String timeRangeParameterId =
          parameters.stream()
              .filter(param -> param.getType() == timeRange)
              .findFirst()
              .orElseThrow()
              .getId();

      Map<String, String> input = new HashMap<>();
      input.put(timeRangeParameterId, String.valueOf(LAST_QUARTER));

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/count/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("interval_count").isEqualTo(0);
      assertThatJson(response).node("previous_interval_count").isEqualTo(0);
      assertThatJson(response).node("difference_count").isEqualTo(0);
    }

    @Test
    @DisplayName("Count all entities with specific filter.")
    void countAllEntitiesWithSpecificFilter() throws Exception {
      endpointComposer
          .forEndpoint(
              EndpointFixture.createDefaultWindowsEndpointWithArch(Endpoint.PLATFORM_ARCH.x86_64))
          .persist();
      endpointComposer
          .forEndpoint(
              EndpointFixture.createDefaultLinuxEndpointWithArch(Endpoint.PLATFORM_ARCH.x86_64))
          .persist();
      Widget widget =
          widgetComposer
              .forWidget(WidgetFixture.createNumberWidgetWithEndpointAndFilter())
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      // force persistence
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async, so the method above
      // completes before the data is available in the system
      Thread.sleep(1000);

      List<CustomDashboardParameters> parameters = widget.getCustomDashboard().getParameters();
      String timeRangeParameterId =
          parameters.stream()
              .filter(param -> param.getType() == timeRange)
              .findFirst()
              .orElseThrow()
              .getId();

      Map<String, String> input = new HashMap<>();
      input.put(timeRangeParameterId, String.valueOf(LAST_QUARTER));

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/count/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("interval_count").isEqualTo(1);
      assertThatJson(response).node("previous_interval_count").isEqualTo(0);
      assertThatJson(response).node("difference_count").isEqualTo(1);
    }

    @Test
    @DisplayName("Count entities with date range filter.")
    void countEntitiesWithDateRangeFilter() throws Exception {
      Endpoint endpoint1 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 1", Endpoint.PLATFORM_TYPE.Windows))
              .persist()
              .get();
      Endpoint endpoint2 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 2", Endpoint.PLATFORM_TYPE.Windows))
              .persist()
              .get();
      Endpoint endpoint3 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 3", Endpoint.PLATFORM_TYPE.Windows))
              .persist()
              .get();

      endpointRepository.setCreationDate(
          Instant.now().minus(180, ChronoUnit.DAYS), endpoint1.getId());
      endpointRepository.setUpdateDate(
          Instant.now().minus(180, ChronoUnit.DAYS), endpoint1.getId());
      endpointRepository.setCreationDate(
          Instant.now().minus(180, ChronoUnit.DAYS), endpoint2.getId());
      endpointRepository.setUpdateDate(
          Instant.now().minus(180, ChronoUnit.DAYS), endpoint2.getId());
      endpointRepository.setCreationDate(
          Instant.now().minus(60, ChronoUnit.DAYS), endpoint3.getId());
      endpointRepository.setUpdateDate(Instant.now().minus(60, ChronoUnit.DAYS), endpoint3.getId());

      Widget widget =
          widgetComposer
              .forWidget(
                  WidgetFixture.createNumberWidgetWithEntityAndTimeRange(
                      "asset", LAST_QUARTER, "base_created_at"))
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      List<CustomDashboardParameters> parameters = widget.getCustomDashboard().getParameters();
      String timeRangeParameterId =
          parameters.stream()
              .filter(param -> param.getType() == timeRange)
              .findFirst()
              .orElseThrow()
              .getId();

      Map<String, String> input = new HashMap<>();
      input.put(timeRangeParameterId, String.valueOf(CustomDashboardTimeRange.LAST_SEMESTER));

      // force persistence
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async, so the method above
      // completes before the data is available in the system
      Thread.sleep(1000);

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/count/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("interval_count").isEqualTo(1);
      assertThatJson(response).node("previous_interval_count").isEqualTo(0);
      assertThatJson(response).node("difference_count").isEqualTo(1);
    }

    @Test
    @DisplayName(
        "Count entities with DEFAULT widget and dashboard parameter ALL_TIME should count all"
            + " entities regardless of age")
    void countEntitiesWithDefaultWidgetAndDashboardParameterAllTime() throws Exception {
      // -- ARRANGE --
      Endpoint endpoint1 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 1", Endpoint.PLATFORM_TYPE.Windows))
              .persist()
              .get();
      Endpoint endpoint2 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 2", Endpoint.PLATFORM_TYPE.Windows))
              .persist()
              .get();
      Endpoint endpoint3 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 3", Endpoint.PLATFORM_TYPE.Linux))
              .persist()
              .get();

      // All endpoints are older than a quarter (the DEFAULT fallback window)
      endpointRepository.setCreationDate(
          Instant.now().minus(200, ChronoUnit.DAYS), endpoint1.getId());
      endpointRepository.setCreationDate(
          Instant.now().minus(300, ChronoUnit.DAYS), endpoint2.getId());
      endpointRepository.setCreationDate(
          Instant.now().minus(400, ChronoUnit.DAYS), endpoint3.getId());

      Widget widget =
          widgetComposer
              .forWidget(
                  WidgetFixture.createNumberWidgetWithEntityAndTimeRange(
                      "asset", DEFAULT, "base_created_at"))
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      List<CustomDashboardParameters> parameters = widget.getCustomDashboard().getParameters();
      String timeRangeParameterId =
          parameters.stream()
              .filter(param -> param.getType() == timeRange)
              .findFirst()
              .orElseThrow()
              .getId();

      Map<String, String> input = new HashMap<>();
      input.put(timeRangeParameterId, String.valueOf(ALL_TIME));

      // force persistence
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async, so the method above
      // completes before the data is available in the system
      Thread.sleep(1000);

      // -- ACT --
      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/count/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      // ALL_TIME must not apply any date lower bound, so all 3 endpoints are counted
      assertThatJson(response).node("interval_count").isEqualTo(3);
      assertThatJson(response).node("previous_interval_count").isEqualTo(0);
      assertThatJson(response).node("difference_count").isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("When fetching series of entities")
  class WhenFetchingEntitiesSeries {

    @Test
    @DisplayName("Fetch series for temporal widgets.")
    void fetchSeriesForTemporalWidgets() throws Exception {
      Endpoint endpoint1 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 1", Endpoint.PLATFORM_TYPE.Windows))
              .persist()
              .get();
      Endpoint endpoint2 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 2", Endpoint.PLATFORM_TYPE.Windows))
              .persist()
              .get();
      Endpoint endpoint3 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 3", Endpoint.PLATFORM_TYPE.Linux))
              .persist()
              .get();
      Endpoint endpoint4 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 4", Endpoint.PLATFORM_TYPE.MacOS))
              .persist()
              .get();

      endpointRepository.setCreationDate(
          Instant.now().minus(83, ChronoUnit.DAYS), endpoint1.getId());
      endpointRepository.setUpdateDate(Instant.now().minus(83, ChronoUnit.DAYS), endpoint1.getId());
      endpointRepository.setCreationDate(
          Instant.now().minus(183, ChronoUnit.DAYS), endpoint2.getId());
      endpointRepository.setUpdateDate(
          Instant.now().minus(183, ChronoUnit.DAYS), endpoint2.getId());
      endpointRepository.setCreationDate(
          Instant.now().minus(83, ChronoUnit.DAYS), endpoint3.getId());
      endpointRepository.setUpdateDate(Instant.now().minus(83, ChronoUnit.DAYS), endpoint3.getId());
      endpointRepository.setCreationDate(
          Instant.now().minus(83, ChronoUnit.DAYS), endpoint4.getId());
      endpointRepository.setUpdateDate(Instant.now().minus(83, ChronoUnit.DAYS), endpoint4.getId());

      Widget widget =
          widgetComposer
              .forWidget(
                  WidgetFixture.creatTemporalWidgetWithTimeRange(
                      LAST_QUARTER, "base_created_at", HistogramInterval.month, "asset"))
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      List<CustomDashboardParameters> parameters = widget.getCustomDashboard().getParameters();
      String timeRangeParameterId =
          parameters.stream()
              .filter(param -> param.getType() == timeRange)
              .findFirst()
              .orElseThrow()
              .getId();

      Map<String, String> input = new HashMap<>();
      input.put(timeRangeParameterId, String.valueOf(LAST_QUARTER));

      // force persistence
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async, so the method above
      // completes before the data is available in the system
      Thread.sleep(1000);

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/series/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<Map<String, Object>> data = JsonPath.read(response, "$[0].data");
      assertThat(data).anyMatch(entry -> (Integer) entry.get("value") == 3);
    }

    @Test
    @DisplayName("Fetch series for structural widgets.")
    void fetchSeriesForStructuralWidgets() throws Exception {
      Endpoint endpoint1 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 1", Endpoint.PLATFORM_TYPE.Windows))
              .persist()
              .get();
      Endpoint endpoint2 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 2", Endpoint.PLATFORM_TYPE.Windows))
              .persist()
              .get();
      Endpoint endpoint3 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 3", Endpoint.PLATFORM_TYPE.Linux))
              .persist()
              .get();
      Endpoint endpoint4 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpointWithPlatform(
                      "Endpoint 4", Endpoint.PLATFORM_TYPE.MacOS))
              .persist()
              .get();

      endpointRepository.setCreationDate(
          Instant.now().minus(83, ChronoUnit.DAYS), endpoint1.getId());
      endpointRepository.setUpdateDate(Instant.now().minus(83, ChronoUnit.DAYS), endpoint1.getId());
      endpointRepository.setCreationDate(
          Instant.now().minus(183, ChronoUnit.DAYS), endpoint2.getId());
      endpointRepository.setUpdateDate(
          Instant.now().minus(183, ChronoUnit.DAYS), endpoint2.getId());
      endpointRepository.setCreationDate(
          Instant.now().minus(83, ChronoUnit.DAYS), endpoint3.getId());
      endpointRepository.setUpdateDate(Instant.now().minus(83, ChronoUnit.DAYS), endpoint3.getId());
      endpointRepository.setCreationDate(
          Instant.now().minus(83, ChronoUnit.DAYS), endpoint4.getId());
      endpointRepository.setUpdateDate(Instant.now().minus(83, ChronoUnit.DAYS), endpoint4.getId());

      Widget widget =
          widgetComposer
              .forWidget(
                  WidgetFixture.createStructuralWidgetWithTimeRange(
                      LAST_QUARTER, "base_created_at", "endpoint_platform", "asset"))
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      // force persistence
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async, so the method above
      // completes before the data is available in the system
      Thread.sleep(1000);

      List<CustomDashboardParameters> parameters = widget.getCustomDashboard().getParameters();
      String timeRangeParameterId =
          parameters.stream()
              .filter(param -> param.getType() == timeRange)
              .findFirst()
              .orElseThrow()
              .getId();

      Map<String, String> input = new HashMap<>();
      input.put(timeRangeParameterId, String.valueOf(LAST_QUARTER));

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/series/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("[0].data").isArray().size().isEqualTo(3);
      assertThatJson(response).node("[0].data[0].value").isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Create List widget in runtime")
  class CreateListWidgetInRuntime {
    // ==================== Common Helper Methods ====================
    private void flushAndProcessElastic() throws InterruptedException {
      entityManager.flush();
      entityManager.clear();
      engineService.bulkProcessing(engineContext.getModels().stream());
      // elastic needs to process the data; it does so async
      Thread.sleep(1000);
    }

    private String performWidgetEntitiesRuntimeRequest(Widget widget, WidgetToEntitiesInput input)
        throws Exception {
      return mvc.perform(
              post(DASHBOARD_URI + "/entities-runtime/" + widget.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    private WidgetToEntitiesInput createWidgetInput(
        Map<String, List<String>> filterValuesMap,
        int seriesIndex,
        Map<String, String> parameters) {
      WidgetToEntitiesInput input = new WidgetToEntitiesInput();
      input.setFilterValuesMap(filterValuesMap);
      input.setSeriesIndex(seriesIndex);
      input.setParameters(parameters);
      return input;
    }

    private Widget createWidgetWithDashboard(Widget widget) {
      return widgetComposer
          .forWidget(widget)
          .withCustomDashboard(
              customDashboardComposer.forCustomDashboard(
                  CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
          .persist()
          .get();
    }

    private EndpointComposer.Composer createEndpoint(Endpoint endpoint) {
      return endpointComposer.forEndpoint(endpoint).persist();
    }

    private Domain createDomain(String name, String colour) {
      return domainComposer
          .forDomain(DomainFixture.getDomainWithNameAndColour(name, colour))
          .persist()
          .get();
    }

    private InjectExpectationComposer.Composer createExpectationComposer(
        BaseInjectExpectation.EXPECTATION_TYPE type,
        BaseInjectExpectation.EXPECTATION_STATUS status) {
      return injectExpectationComposer.forExpectation(
          InjectExpectationFixture.createExpectationWithTypeAndStatus(type, status));
    }

    private Inject createTechnicalInject(
        Domain domain,
        AttackPattern attackPattern,
        List<InjectExpectationComposer.Composer> expectations) {
      EndpointComposer.Composer endpointWrapper = createEndpoint(EndpointFixture.createEndpoint());
      InjectorContractComposer.Composer injectorContract;

      if (domain != null) {
        injectorContract =
            injectorContractComposer.forInjectorContract(
                InjectorContractFixture.createInjectorContractWithDomain(domain));
      } else {
        injectorContract =
            injectorContractComposer.forInjectorContract(
                InjectorContractFixture.createDefaultInjectorContract());
      }

      if (attackPattern != null) {
        injectorContract.withAttackPattern(attackPatternComposer.forAttackPattern(attackPattern));
      }

      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointWrapper)
              .withInjectorContract(injectorContract);

      expectations.forEach(exp -> injectWrapper.withExpectation(exp.withEndpoint(endpointWrapper)));

      return injectWrapper.persist().get();
    }

    // ==================== End Common Helper Methods ====================

    @Test
    @DisplayName(
        "Given Structural Endpoint Histogram breakdown by platform, should return list of windows"
            + " endpoint")
    void given_structuralEndpointHistogram_should_returnListOfWindowsEndpoint() throws Exception {
      createEndpoint(
          EndpointFixture.createEndpointWithPlatform("Endpoint A", Endpoint.PLATFORM_TYPE.Windows));
      createEndpoint(
          EndpointFixture.createEndpointWithPlatform("Endpoint B", Endpoint.PLATFORM_TYPE.Windows));
      createEndpoint(
          EndpointFixture.createEndpointWithPlatform("Endpoint C", Endpoint.PLATFORM_TYPE.Linux));
      createEndpoint(
          EndpointFixture.createEndpointWithPlatform("Endpoint D", Endpoint.PLATFORM_TYPE.MacOS));

      Widget widget =
          createWidgetWithDashboard(
              WidgetFixture.createStructuralWidgetWithTimeRange(
                  LAST_QUARTER, "base_created_at", "endpoint_platform", "asset"));

      flushAndProcessElastic();

      // Build request
      List<CustomDashboardParameters> parameters = widget.getCustomDashboard().getParameters();
      String timeRangeParameterId =
          parameters.stream()
              .filter(param -> param.getType() == timeRange)
              .findFirst()
              .orElseThrow()
              .getId();

      WidgetToEntitiesInput input =
          createWidgetInput(
              Map.of("endpoint_platform", List.of(Endpoint.PLATFORM_TYPE.Windows.name())),
              0,
              Map.of(timeRangeParameterId, String.valueOf(ALL_TIME)));

      // Execute & Assert
      String response = performWidgetEntitiesRuntimeRequest(widget, input);
      assertThatJson(response)
          .node("list_configuration.perspective.filter.filters")
          .isArray()
          .hasSize(2);
      assertThatJson(response)
          .node("list_configuration.perspective.filter.filters")
          .isArray()
          .anySatisfy(
              filter -> {
                assertThatJson(filter).node("key").isEqualTo("base_entity");
                assertThatJson(filter).node("values").isArray().containsExactly("asset");
              })
          .anySatisfy(
              filter -> {
                assertThatJson(filter).node("key").isEqualTo("endpoint_platform");
                assertThatJson(filter).node("values").isArray().containsExactly("Windows");
              });
      assertThatJson(response).node("es_entities.total").isEqualTo(2);
      assertThatJson(response).node("es_entities.es_datas").isArray().size().isEqualTo(2);
    }

    @Test
    @DisplayName("Given security coverage widget should return list of inject expectations")
    void given_securityCoverageWidget_should_returnListOfInjectExpectations() throws Exception {
      AttackPattern attackPattern1 =
          attackPatternRepository.save(AttackPatternFixture.createDefaultAttackPattern());
      AttackPattern attackPattern2 =
          attackPatternRepository.save(AttackPatternFixture.createDefaultAttackPattern());
      AttackPattern attackPattern3 =
          attackPatternRepository.save(AttackPatternFixture.createDefaultAttackPattern());
      Inject inject1 =
          createTechnicalInject(
              null,
              attackPattern1,
              List.of(
                  createExpectationComposer(
                      BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                      BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS),
                  createExpectationComposer(
                      BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                      BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS)));
      Inject inject2 =
          createTechnicalInject(
              null,
              attackPattern1,
              List.of(
                  createExpectationComposer(
                      BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                      BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS),
                  createExpectationComposer(
                      BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                      BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS)));
      Inject inject3 =
          createTechnicalInject(
              null,
              attackPattern2,
              List.of(
                  createExpectationComposer(
                      BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                      BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS),
                  createExpectationComposer(
                      BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                      BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS)));
      createTechnicalInject(
          null,
          attackPattern3,
          List.of(
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS),
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS)));

      Widget widget =
          createWidgetWithDashboard(
              WidgetFixture.createSecurityConverageWidget(
                  ALL_TIME, "base_created_at", BaseInjectExpectation.EXPECTATION_TYPE.DETECTION));

      flushAndProcessElastic();

      // Build request
      WidgetToEntitiesInput input =
          createWidgetInput(
              Map.of(
                  "base_attack_patterns_side",
                  List.of(attackPattern1.getId(), attackPattern2.getId())),
              0,
              new HashMap<>());

      // Execute & Assert
      String response = performWidgetEntitiesRuntimeRequest(widget, input);

      assertThatJson(response)
          .node("list_configuration.perspective.filter.filters")
          .isArray()
          .anySatisfy(
              filter -> {
                assertThatJson(filter).node("key").isEqualTo("base_entity");
                assertThatJson(filter)
                    .node("values")
                    .isArray()
                    .containsExactly("expectation-inject");
              });
      assertThatJson(response)
          .node("es_entities.es_datas")
          .isArray()
          .hasSize(6)
          .extracting("base_inject_side")
          .containsOnly(inject1.getId(), inject2.getId(), inject3.getId());
      assertThatJson(response).node("es_entities.total").isEqualTo(6);
    }

    /** Sums every bucket of every charted series - what the tile actually renders. */
    private int tileTotal(Widget widget) throws Exception {
      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/series/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(new HashMap<String, String>()))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      List<Integer> bucketValues = JsonPath.read(response, "$..data[*].value");
      return bucketValues.stream().mapToInt(Integer::intValue).sum();
    }

    private WidgetToEntitiesInput seriesDrillDown(List<Integer> seriesIndexes) {
      WidgetToEntitiesInput input = new WidgetToEntitiesInput();
      input.setSeriesIndexes(seriesIndexes);
      input.setParameters(new HashMap<>());
      return input;
    }

    /**
     * Guards the invariant behind #7079: a tile's number and the list you reach by clicking it must
     * describe the same documents. The command center's ADVERSARY node totals several series at
     * once, which a single {@code series_index} cannot express - the drill-down used to restate the
     * scope as literal status values instead, and silently drifted from the widget definition
     * (showing 747 while the list returned 795 in production). Naming the aggregated series makes
     * both read one definition.
     *
     * <p>The same data is charted by two widgets so the drilled list is shown to follow the
     * declared series rather than simply returning everything: a resolved-only widget must leave
     * the pending expectations out. Only SUCCESS / FAILED / PENDING are exercised because no other
     * status is reachable - {@code computeStatus} never returns PARTIAL, and returns UNKNOWN only
     * for a null expected score, which migration V3_36 forbids.
     */
    @Test
    @DisplayName("Given a total spanning several series, drilling it returns exactly that total")
    void given_totalSpanningSeveralSeries_should_returnExactlyThatTotal() throws Exception {
      createTechnicalInject(
          null,
          null,
          List.of(
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS),
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.FAILED),
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.FAILED),
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.PENDING),
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.PENDING)));

      Widget attempted =
          createWidgetWithDashboard(
              WidgetFixture.createExpectationStatusSeriesWidget(
                  ALL_TIME,
                  "base_created_at",
                  List.of(
                      BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                      BaseInjectExpectation.EXPECTATION_STATUS.FAILED,
                      BaseInjectExpectation.EXPECTATION_STATUS.PENDING)));
      Widget resolvedOnly =
          createWidgetWithDashboard(
              WidgetFixture.createExpectationStatusSeriesWidget(
                  ALL_TIME,
                  "base_created_at",
                  List.of(
                      BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                      BaseInjectExpectation.EXPECTATION_STATUS.FAILED)));

      flushAndProcessElastic();

      // Every attempted validation: the tile counts 5 and the list must hold those same 5.
      assertThat(tileTotal(attempted)).isEqualTo(5);
      assertThatJson(
              performWidgetEntitiesRuntimeRequest(attempted, seriesDrillDown(List.of(0, 1, 2))))
          .node("es_entities.total")
          .isEqualTo(tileTotal(attempted));

      // Same data, fewer declared series: the drill-down stays bounded by them and drops
      // the two pending expectations instead of returning everything.
      assertThat(tileTotal(resolvedOnly)).isEqualTo(3);
      assertThatJson(
              performWidgetEntitiesRuntimeRequest(resolvedOnly, seriesDrillDown(List.of(0, 1))))
          .node("es_entities.total")
          .isEqualTo(tileTotal(resolvedOnly));

      // A single index still scopes to that one series.
      assertThatJson(performWidgetEntitiesRuntimeRequest(attempted, seriesDrillDown(List.of(0))))
          .node("es_entities.total")
          .isEqualTo(1);
    }

    /**
     * The flat per-key union that collapses a multi-series OR is only equivalent to it when the
     * series diverge on exactly one key. Here they diverge on two (and the second series' types are
     * a subset of the first's, so the union of that key does not even grow), which would admit a
     * DETECTION/FAILED document matching neither series - reject instead of over-counting.
     */
    @Test
    @DisplayName("Given series diverging on two keys, drilling them together is rejected")
    void given_seriesDivergingOnTwoKeys_should_rejectTheDrillDown() throws Exception {
      Widget widget =
          createWidgetWithDashboard(
              WidgetFixture.createDivergentSeriesWidget(ALL_TIME, "base_created_at"));

      flushAndProcessElastic();

      mvc.perform(
              post(DASHBOARD_URI + "/entities-runtime/" + widget.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(seriesDrillDown(List.of(0, 1))))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    /**
     * The series indexes travel as client-controlled URL parameters, so one the widget does not
     * declare has to answer 400 - even alongside valid indexes, since silently dropping it would
     * detach the drilled list from the number the caller believes it is expanding. Resolving to an
     * empty series instead would carry a null filter list - Lombok drops the initializer under
     * {@code @Builder.Default}, which is why {@link io.openaev.database.model.Filters} null-guards
     * it everywhere - and surface as an opaque 500.
     */
    @Test
    @DisplayName("Given a series index the widget does not declare, the drill-down is rejected")
    void given_unknownSeriesIndex_should_rejectTheDrillDown() throws Exception {
      Widget widget =
          createWidgetWithDashboard(
              WidgetFixture.createExpectationStatusSeriesWidget(
                  ALL_TIME,
                  "base_created_at",
                  List.of(BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS)));

      flushAndProcessElastic();

      mvc.perform(
              post(DASHBOARD_URI + "/entities-runtime/" + widget.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(seriesDrillDown(List.of(99))))
                  .with(csrf()))
          .andExpect(status().isBadRequest());

      // A valid index does not excuse an unknown one riding along with it.
      mvc.perform(
              post(DASHBOARD_URI + "/entities-runtime/" + widget.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(seriesDrillDown(List.of(0, 99))))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    /**
     * The per-key value union that collapses a multi-series OR presumes both series select
     * documents the same way on that key. Series using different operators (here eq vs not_eq on
     * the same status values) have no such collapse - the merged filter would keep one operator and
     * mean something neither series said - so the drill-down must reject the shape.
     */
    @Test
    @DisplayName("Given series using different operators on a key, drilling them is rejected")
    void given_seriesWithDifferentOperators_should_rejectTheDrillDown() throws Exception {
      Widget widget =
          createWidgetWithDashboard(
              WidgetFixture.createStatusSeriesWidgetWithOperators(
                  ALL_TIME,
                  "base_created_at",
                  Filters.FilterOperator.eq,
                  List.of(BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS.name()),
                  Filters.FilterOperator.not_eq,
                  List.of(BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS.name())));

      flushAndProcessElastic();

      mvc.perform(
              post(DASHBOARD_URI + "/entities-runtime/" + widget.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(seriesDrillDown(List.of(0, 1))))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    /**
     * Negated operators never union soundly: the engine turns every not_eq value into a must-not
     * clause - a conjunction of exclusions - so {@code NOT SUCCESS} unioned with {@code NOT FAILED}
     * would exclude both statuses when the OR of the series excludes neither everywhere. The
     * drill-down must reject the divergence instead of quietly narrowing the list.
     */
    @Test
    @DisplayName("Given series diverging under a negated operator, drilling them is rejected")
    void given_seriesDivergingUnderNegatedOperator_should_rejectTheDrillDown() throws Exception {
      Widget widget =
          createWidgetWithDashboard(
              WidgetFixture.createStatusSeriesWidgetWithOperators(
                  ALL_TIME,
                  "base_created_at",
                  Filters.FilterOperator.not_eq,
                  List.of(BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS.name()),
                  Filters.FilterOperator.not_eq,
                  List.of(BaseInjectExpectation.EXPECTATION_STATUS.FAILED.name())));

      flushAndProcessElastic();

      mvc.perform(
              post(DASHBOARD_URI + "/entities-runtime/" + widget.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(seriesDrillDown(List.of(0, 1))))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "Given security domain widget should return list of expectation filtered by domain, type"
            + " and status")
    void given_securityDomainWidget_should_returnListOfExpectationFilteredByDomain()
        throws Exception {
      Domain networkDomain = createDomain("Network-test", "red");
      Domain endpointDomain = createDomain("Endpoint-test", "blue");

      createTechnicalInject(
          networkDomain,
          null,
          List.of(
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.FAILED),
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS),
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS)));
      createTechnicalInject(
          networkDomain,
          null,
          List.of(
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS)));
      createTechnicalInject(
          endpointDomain,
          null,
          List.of(
              createExpectationComposer(
                  BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
                  BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS)));

      Widget widget =
          createWidgetWithDashboard(
              WidgetFixture.createSecurityDomainWidget(DEFAULT, "base_created_at"));

      flushAndProcessElastic();

      Map<String, List<String>> filterValuesMap = new HashMap<>();
      filterValuesMap.put("base_security_domains_side", List.of(networkDomain.getId()));
      filterValuesMap.put("inject_expectation_status", List.of("SUCCESS"));
      filterValuesMap.put("inject_expectation_type", List.of("DETECTION"));
      WidgetToEntitiesInput input = createWidgetInput(filterValuesMap, 0, new HashMap<>());

      // Execute & Assert
      String response = performWidgetEntitiesRuntimeRequest(widget, input);

      assertThatJson(response)
          .node("list_configuration.perspective.filter.filters")
          .isArray()
          .anySatisfy(
              filter -> {
                assertThatJson(filter).node("key").isEqualTo("base_entity");
                assertThatJson(filter)
                    .node("values")
                    .isArray()
                    .containsExactly("expectation-inject");
              });
      assertThatJson(response)
          .node("list_configuration.perspective.filter.filters")
          .isArray()
          .anySatisfy(
              filter -> {
                assertThatJson(filter).node("key").isEqualTo("base_security_domains_side");
                assertThatJson(filter)
                    .node("values")
                    .isArray()
                    .containsExactly(networkDomain.getId());
              });

      assertThatJson(response).node("es_entities.es_datas").isArray().hasSize(2);
    }
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    @Test
    @DisplayName("Custom dashboard created in tenant X should NOT be readable from tenant Y")
    void given_customDashboardInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_DASHBOARDS));

      CustomDashboardInput input = new CustomDashboardInput();
      input.setName("Isolation Test Dashboard");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/custom-dashboards")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String dashboardId = JsonPath.read(createResponse, "$.custom_dashboard_id");

      entityManager.flush();
      entityManager.clear();

      // -------- Act — read from tenant Y (expect 404) --------
      int responseStatus =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/custom-dashboards/" + dashboardId)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Custom dashboard created in tenant X should be readable from tenant X")
    void given_customDashboardInTenantX_should_beReadableFromTenantX() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));

      CustomDashboardInput input = new CustomDashboardInput();
      input.setName("Same Tenant Dashboard");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/custom-dashboards")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String dashboardId = JsonPath.read(createResponse, "$.custom_dashboard_id");

      // -------- Act & Assert — read from same tenant should succeed --------
      mvc.perform(
              get("/api/tenants/" + tenantX.getId() + "/custom-dashboards/" + dashboardId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(
              result ->
                  assertThatJson(result.getResponse().getContentAsString())
                      .node("custom_dashboard_name")
                      .isEqualTo("Same Tenant Dashboard"));
    }

    @Test
    @DisplayName("Custom dashboard search in tenant Y should NOT return dashboards from tenant X")
    void given_customDashboardInTenantX_should_notAppearInTenantYSearch() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_DASHBOARDS));

      CustomDashboardInput input = new CustomDashboardInput();
      input.setName("CrossTenantSearchDashboard");

      mvc.perform(
              post("/api/tenants/" + tenantX.getId() + "/custom-dashboards")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      entityManager.flush();
      entityManager.clear();

      // -------- Act — search from tenant Y --------
      SearchPaginationInput searchInput =
          PaginationFixture.simpleTextSearch("CrossTenantSearchDashboard");

      String searchResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/custom-dashboards/search")
                      .content(asJsonString(searchInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals(Integer.valueOf(0), JsonPath.read(searchResponse, "$.totalElements"));
    }

    @Test
    @DisplayName("Custom dashboard created in tenant X should NOT be updatable from tenant Y")
    void given_customDashboardInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));

      CustomDashboardInput input = new CustomDashboardInput();
      input.setName("Update Isolation Test Dashboard");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/custom-dashboards")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String dashboardId = JsonPath.read(createResponse, "$.custom_dashboard_id");

      entityManager.flush();
      entityManager.clear();

      // -------- Act — update from tenant Y (expect 404) --------
      CustomDashboardInput updateInput = new CustomDashboardInput();
      updateInput.setName("Hijacked Name");

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/" + tenantY.getId() + "/custom-dashboards/" + dashboardId)
                      .content(asJsonString(updateInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Custom dashboard created in tenant X should NOT be deletable from tenant Y")
    void given_customDashboardInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.DELETE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));

      CustomDashboardInput input = new CustomDashboardInput();
      input.setName("Delete Isolation Test Dashboard");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/custom-dashboards")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String dashboardId = JsonPath.read(createResponse, "$.custom_dashboard_id");

      entityManager.flush();
      entityManager.clear();

      // -------- Act — delete from tenant Y (expect 404) --------
      int responseStatus =
          mvc.perform(
                  delete("/api/tenants/" + tenantY.getId() + "/custom-dashboards/" + dashboardId)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName(
        "Widget data endpoint in tenant Y should NOT return data for widget belonging to tenant X")
    void given_widgetInTenantX_should_notBeAccessibleFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_DASHBOARDS));

      CustomDashboardInput dashboardInput = new CustomDashboardInput();
      dashboardInput.setName("Widget Isolation Dashboard");

      mvc.perform(
              post("/api/tenants/" + tenantX.getId() + "/custom-dashboards")
                  .content(asJsonString(dashboardInput))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Create a widget via composers in tenant X context
      tenantIsolationHelper.switchToTenant(tenantX.getId(), entityManager);
      Widget widget =
          widgetComposer
              .forWidget(WidgetFixture.createNumberWidgetWithEntity("asset"))
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      entityManager.flush();
      entityManager.clear();

      // -------- Act — access widget data from tenant Y (expect 404) --------
      int response =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/dashboards/count/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(new HashMap<>()))
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getContentLength();

      // -------- Assert --------
      assertThat(response).isEqualTo(0);
    }
  }
}
