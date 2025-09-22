package io.openbas.rest.dashboard;

import static io.openbas.database.model.CustomDashboardParameters.CustomDashboardParameterType.timeRange;
import static io.openbas.rest.dashboard.DashboardApi.DASHBOARD_URI;
import static io.openbas.utils.CustomDashboardTimeRange.ALL_TIME;
import static io.openbas.utils.CustomDashboardTimeRange.LAST_QUARTER;
import static io.openbas.utils.JsonUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.model.CustomDashboardParameters;
import io.openbas.database.model.Endpoint;
import io.openbas.database.model.Filters;
import io.openbas.database.model.Widget;
import io.openbas.database.repository.AttackPatternRepository;
import io.openbas.database.repository.EndpointRepository;
import io.openbas.engine.EngineContext;
import io.openbas.engine.EngineService;
import io.openbas.engine.EsModel;
import io.openbas.engine.api.*;
import io.openbas.engine.api.EngineSortField;
import io.openbas.engine.api.HistogramInterval;
import io.openbas.engine.api.ListConfiguration;
import io.openbas.engine.api.SortDirection;
import io.openbas.rest.dashboard.model.WidgetToEntitiesInput;
import io.openbas.utils.CustomDashboardTimeRange;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.*;
import io.openbas.utils.fixtures.files.AttackPatternFixture;
import io.openbas.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
              .forWidget(WidgetFixture.createListWidgetWithEntity("endpoint"))
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
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("[0].base_id").isEqualTo(ep.getId());
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

      Widget listWidget = WidgetFixture.createListWidgetWithEntity("endpoint");
      EngineSortField sortField = new EngineSortField();
      sortField.setFieldName("endpoint_hostname");
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
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("[0].base_id").isEqualTo(epWrapper1.get().getId());
      assertThatJson(response).node("[1].base_id").isEqualTo(epWrapper2.get().getId());
      assertThatJson(response).node("[2].base_id").isEqualTo(epWrapper3.get().getId());
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

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/entities/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"%s\":\"%s\"}"
                              .formatted(
                                  paramWrapper.get().getId(), exerciseWrapper1.get().getId())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .node("[0].vulnerable_endpoint_id")
          .isEqualTo(epWrapper2.get().getId());
      assertThatJson(response)
          .node("[1].vulnerable_endpoint_id")
          .isEqualTo(epWrapper1.get().getId());
      assertThatJson(response).isArray().size().isEqualTo(2);
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
              .forWidget(WidgetFixture.createNumberWidgetWithEntity("endpoint"))
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
          parameters.stream().filter(param -> param.getType() == timeRange).toString();

      Map<String, String> input = new HashMap<>();
      input.put(timeRangeParameterId, String.valueOf(LAST_QUARTER));

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/count/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isEqualTo(3);
    }

    @Test
    @DisplayName("Count no entity with no specific filter.")
    void countNoEntityWithNoSpecificFilter() throws Exception {
      Widget widget =
          widgetComposer
              .forWidget(WidgetFixture.createNumberWidgetWithEntity("endpoint"))
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
          parameters.stream().filter(param -> param.getType() == timeRange).toString();

      Map<String, String> input = new HashMap<>();
      input.put(timeRangeParameterId, String.valueOf(LAST_QUARTER));

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/count/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isEqualTo(0);
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
          parameters.stream().filter(param -> param.getType() == timeRange).toString();

      Map<String, String> input = new HashMap<>();
      input.put(timeRangeParameterId, String.valueOf(LAST_QUARTER));

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/count/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isEqualTo(1);
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
                      "endpoint", LAST_QUARTER, "base_created_at"))
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      List<CustomDashboardParameters> parameters = widget.getCustomDashboard().getParameters();
      String timeRangeParameterId =
          parameters.stream().filter(param -> param.getType() == timeRange).toString();

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
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isEqualTo(1);
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
                      LAST_QUARTER, "base_created_at", HistogramInterval.month, "endpoint"))
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(
                      CustomDashboardFixture.createCustomDashboardWithDefaultParams()))
              .persist()
              .get();

      List<CustomDashboardParameters> parameters = widget.getCustomDashboard().getParameters();
      String timeRangeParameterId =
          parameters.stream().filter(param -> param.getType() == timeRange).toString();

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
                      .content(asJsonString(input)))
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
                      LAST_QUARTER, "base_created_at", "endpoint_platform", "endpoint"))
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
          parameters.stream().filter(param -> param.getType() == timeRange).toString();

      Map<String, String> input = new HashMap<>();
      input.put(timeRangeParameterId, String.valueOf(LAST_QUARTER));

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/series/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
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
    private void createEndpoint(String name, Endpoint.PLATFORM_TYPE platform) {
      endpointComposer
          .forEndpoint(EndpointFixture.createEndpointWithPlatform(name, platform))
          .persist();
    }

    @Test
    @DisplayName(
        "Given Structural Endpoint Histogram breakdown by platform, should return list of windows endpoint")
    void given_structuralEndpointHistogram_should_returnListOfWindowsEndpoint() throws Exception {
      createEndpoint("Endpoint A", Endpoint.PLATFORM_TYPE.Windows);
      createEndpoint("Endpoint B", Endpoint.PLATFORM_TYPE.Windows);
      createEndpoint("Endpoint C", Endpoint.PLATFORM_TYPE.Linux);
      createEndpoint("Endpoint D", Endpoint.PLATFORM_TYPE.MacOS);
      Widget widget =
          widgetComposer
              .forWidget(
                  WidgetFixture.createStructuralWidgetWithTimeRange(
                      LAST_QUARTER, "base_created_at", "endpoint_platform", "endpoint"))
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
          parameters.stream().filter(param -> param.getType() == timeRange).toString();
      Map<String, String> parameterInput = new HashMap<>();
      parameterInput.put(timeRangeParameterId, String.valueOf(ALL_TIME));

      WidgetToEntitiesInput input = new WidgetToEntitiesInput();
      input.setFilterValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
      input.setSeriesIndex(0);
      input.setParameters(parameterInput);

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/entities-runtime/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
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
                assertThatJson(filter).node("values").isArray().containsExactly("endpoint");
              })
          .anySatisfy(
              filter -> {
                assertThatJson(filter).node("key").isEqualTo("endpoint_platform");
                assertThatJson(filter).node("values").isArray().containsExactly("Windows");
              });
      assertThatJson(response).node("es_entities").isArray().size().isEqualTo(2);
    }

    private Inject createInjectWithDetectionExpectation(AttackPattern attackPattern) {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      InjectExpectation detection1 =
          InjectExpectationFixture.createExpectationWithTypeAndStatus(
              InjectExpectation.EXPECTATION_TYPE.DETECTION,
              InjectExpectation.EXPECTATION_STATUS.SUCCESS);
      InjectExpectation detection2 =
          InjectExpectationFixture.createExpectationWithTypeAndStatus(
              InjectExpectation.EXPECTATION_TYPE.DETECTION,
              InjectExpectation.EXPECTATION_STATUS.SUCCESS);
      return injectComposer
          .forInject(InjectFixture.getDefaultInject())
          .withEndpoint(endpointWrapper)
          .withInjectorContract(
              injectorContractComposer
                  .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                  .withAttackPattern(attackPatternComposer.forAttackPattern(attackPattern)))
          .withExpectation(
              injectExpectationComposer.forExpectation(detection1).withEndpoint(endpointWrapper))
          .withExpectation(
              injectExpectationComposer.forExpectation(detection2).withEndpoint(endpointWrapper))
          .persist()
          .get();
    }

    @Test
    @DisplayName("Given security coverage widget should return list of inject")
    void given_securityCoverageWidget_should_returnListOfInject() throws Exception {
      AttackPattern attackPattern1 =
          attackPatternRepository.save(AttackPatternFixture.createDefaultAttackPattern());
      AttackPattern attackPattern2 =
          attackPatternRepository.save(AttackPatternFixture.createDefaultAttackPattern());
      AttackPattern attackPattern3 =
          attackPatternRepository.save(AttackPatternFixture.createDefaultAttackPattern());
      Inject inject1 = createInjectWithDetectionExpectation(attackPattern1);
      Inject inject2 = createInjectWithDetectionExpectation(attackPattern1);
      Inject inject3 = createInjectWithDetectionExpectation(attackPattern2);
      createInjectWithDetectionExpectation(attackPattern3);
      Widget widget =
          widgetComposer
              .forWidget(
                  WidgetFixture.createSecurityConverageWidget(
                      ALL_TIME, "base_created_at", InjectExpectation.EXPECTATION_TYPE.DETECTION))
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

      WidgetToEntitiesInput input = new WidgetToEntitiesInput();
      input.setFilterValues(List.of(attackPattern1.getId(), attackPattern2.getId()));
      input.setSeriesIndex(0);
      input.setParameters(new HashMap<>());

      String response =
          mvc.perform(
                  post(DASHBOARD_URI + "/entities-runtime/" + widget.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response)
          .node("list_configuration.perspective.filter.filters")
          .isArray()
          .anySatisfy(
              filter -> {
                assertThatJson(filter).node("key").isEqualTo("base_entity");
                assertThatJson(filter).node("values").isArray().containsExactly("inject");
              });
      assertThatJson(response)
          .node("es_entities")
          .isArray()
          .hasSize(3)
          .extracting("base_id")
          .containsExactlyInAnyOrder(inject1.getId(), inject2.getId(), inject3.getId());
    }
  }
}
