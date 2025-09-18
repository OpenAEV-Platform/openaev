package io.openbas.rest.dashboard;

import static io.openbas.config.SessionHelper.currentUser;

import io.openbas.database.model.*;
import io.openbas.database.raw.RawUserAuth;
import io.openbas.database.raw.RawUserAuthFlat;
import io.openbas.database.repository.UserRepository;
import io.openbas.engine.EngineService;
import io.openbas.engine.api.*;
import io.openbas.engine.model.EsBase;
import io.openbas.engine.model.EsSearch;
import io.openbas.engine.model.injectexpectation.EsInjectExpectation;
import io.openbas.engine.query.EsAttackPath;
import io.openbas.engine.query.EsSeries;
import io.openbas.rest.custom_dashboard.WidgetService;
import io.openbas.rest.custom_dashboard.utils.WidgetUtils;
import io.openbas.rest.dashboard.model.WidgetToEntitiesInput;
import io.openbas.rest.dashboard.model.WidgetToEntitiesOutput;
import io.openbas.service.EsAttackPathService;
import io.openbas.utils.mapper.RawUserAuthMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class DashboardService {

  private final EsAttackPathService esAttackPathService;
  private final EngineService engineService;
  private final UserRepository userRepository;
  private final WidgetService widgetService;

  private final RawUserAuthMapper rawUserAuthMapper;

  /**
   * Retrieves count data from Elasticsearch for a specific widget based on its configuration.
   *
   * @param widgetId the id from the {@link Widget} defining the type and configuration
   * @param parameters parameters passed at runtime (e.g. filters, date ranges)
   * @return long representing the count result
   */
  public long count(String widgetId, Map<String, String> parameters) {
    WidgetContext widgetContext = getWidgetContext(widgetId, parameters);
    FlatConfiguration config = (FlatConfiguration) widgetContext.widget().getWidgetConfiguration();
    CountRuntime runtime =
        new CountRuntime(config, widgetContext.parameters(), widgetContext.definitionParameters());
    return engineService.count(widgetContext.user(), runtime);
  }

  /**
   * Retrieves time series or structural histogram data from Elasticsearch for a specific widget
   * based on its configuration.
   *
   * @param widgetId the id from the {@link Widget} defining the type and configuration
   * @param parameters parameters passed at runtime (e.g. filters, date ranges)
   * @return list of {@link EsSeries} representing series data suitable for charting
   * @throws RuntimeException if the widget type is unsupported
   */
  public List<EsSeries> series(String widgetId, Map<String, String> parameters) {
    WidgetContext widgetContext = getWidgetContext(widgetId, parameters);
    if (WidgetConfigurationType.TEMPORAL_HISTOGRAM.equals(
        widgetContext.widget().getWidgetConfiguration().getConfigurationType())) {
      DateHistogramWidget config =
          (DateHistogramWidget) widgetContext.widget().getWidgetConfiguration();
      DateHistogramRuntime runtime =
          new DateHistogramRuntime(
              config, widgetContext.parameters(), widgetContext.definitionParameters());
      return engineService.multiDateHistogram(widgetContext.user(), runtime);
    } else if (WidgetConfigurationType.STRUCTURAL_HISTOGRAM.equals(
        widgetContext.widget().getWidgetConfiguration().getConfigurationType())) {
      StructuralHistogramWidget config =
          (StructuralHistogramWidget) widgetContext.widget().getWidgetConfiguration();
      StructuralHistogramRuntime runtime =
          new StructuralHistogramRuntime(
              config, widgetContext.parameters(), widgetContext.definitionParameters());
      return engineService.multiTermHistogram(widgetContext.user(), runtime);
    }
    throw new UnsupportedOperationException("Unsupported widget: " + widgetContext.widget());
  }

  /**
   * Executes a list query using the provided widget context and configuration.
   *
   * @param widgetContext the context containing widget, user, and parameter information
   * @param config the list configuration defining query parameters
   * @return a list of entities retrieved from the engine service
   */
  private List<EsBase> executeListQuery(WidgetContext widgetContext, ListConfiguration config) {
    ListRuntime runtime =
        new ListRuntime(config, widgetContext.parameters(), widgetContext.definitionParameters());
    return engineService.entities(widgetContext.user(), runtime);
  }

  /**
   * Retrieves a list of entities from Elasticsearch for a widget configured as a list.
   *
   * @param widgetId the id from the {@link Widget} with a list configuration
   * @param parameters parameters passed at runtime (e.g. filters)
   * @return list of {@link EsBase} entities matching the list widget query
   */
  public List<EsBase> entities(String widgetId, Map<String, String> parameters) {
    WidgetContext widgetContext = getWidgetContext(widgetId, parameters);
    ListConfiguration config = (ListConfiguration) widgetContext.widget().getWidgetConfiguration();
    return executeListQuery(widgetContext, config);
  }

  /**
   * Checks if the given widget is a Security Coverage chart widget.
   *
   * @param widget the widget to check
   * @return true if the widget is of type SECURITY_COVERAGE_CHART, false otherwise
   */
  private boolean isSecurityCoverageWidget(Widget widget) {
    return WidgetType.SECURITY_COVERAGE_CHART.equals(widget.getType());
  }

  /**
   * Retrieves inject expectations list by attackPatterns ids
   *
   * @param widgetContext the widget context containing user and widget information
   * @param attackPatternIds list of attack pattern IDs to filter by
   * @return list of inject expectations with SUCCESS or FAILED status
   */
  private List<EsInjectExpectation> getSecurityCoverageWidgetResultsByTTPs(
      WidgetContext widgetContext, List<String> attackPatternIds) {
    ListConfiguration listInjectExpectationsConfig =
        widgetService.convertWidgetToListConfiguration(widgetContext.widget, 0, attackPatternIds);
    List<String> statusFilters =
        List.of(
            InjectExpectation.EXPECTATION_STATUS.FAILED.name(),
            InjectExpectation.EXPECTATION_STATUS.SUCCESS.name());
    WidgetUtils.setOrAddFilterByKey(
        listInjectExpectationsConfig.getPerspective().getFilter(),
        "inject_expectation_status",
        statusFilters,
        Filters.FilterOperator.contains);
    return executeListQuery(widgetContext, listInjectExpectationsConfig).stream()
        .map(EsInjectExpectation.class::cast)
        .toList();
  }

  /**
   * Converts a widget to a list configuration and retrieves corresponding entities. Handles special
   * case for Security Coverage widgets which require a two-step process.
   *
   * @param widgetId the unique identifier of the widget
   * @param input contains parameters, series index, and filter value for the conversion
   * @return output containing both the generated list configuration and retrieved entities
   */
  public WidgetToEntitiesOutput widgetToEntitiesRuntime(
      String widgetId, WidgetToEntitiesInput input) {
    WidgetContext widgetContext = getWidgetContext(widgetId, input.getParameters());
    ListConfiguration listConfig;
    List<EsBase> datas;

    if (isSecurityCoverageWidget(widgetContext.widget)) {
      List<EsInjectExpectation> results =
          getSecurityCoverageWidgetResultsByTTPs(widgetContext, input.getFilterValues());
      listConfig = widgetService.convertSecurityCoverageResultsToInjectListConfig(results);
      datas = results.isEmpty() ? List.of() : executeListQuery(widgetContext, listConfig);
    } else {
      listConfig =
          widgetService.convertWidgetToListConfiguration(
              widgetContext.widget, input.getSeriesIndex(), input.getFilterValues());
      datas = executeListQuery(widgetContext, listConfig);
    }

    return WidgetToEntitiesOutput.builder().listConfiguration(listConfig).esEntities(datas).build();
  }

  /**
   * Retrieves a list of EsAttackPath data from Elasticsearch for an attack path widget.
   *
   * @param widgetId the unique identifier of the widget
   * @param parameters parameters passed at runtime (e.g. filters, date ranges)
   * @return list of {@link EsAttackPath} representing data suitable for charting the AttachPath
   *     widget
   * @throws RuntimeException if the widget type is unsupported
   */
  public List<EsAttackPath> attackPaths(String widgetId, Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    WidgetContext widgetContext = getWidgetContext(widgetId, parameters);
    StructuralHistogramWidget config =
        (StructuralHistogramWidget) widgetContext.widget().getWidgetConfiguration();
    StructuralHistogramRuntime runtime =
        new StructuralHistogramRuntime(
            config, widgetContext.parameters(), widgetContext.definitionParameters());
    return esAttackPathService.attackPaths(
        widgetContext.user(),
        runtime,
        widgetContext.parameters(),
        widgetContext.definitionParameters());
  }

  /**
   * Executes a global search query in Elasticsearch for the current user.
   *
   * @param search the search text
   * @return list of {@link EsSearch} search results
   */
  public List<EsSearch> search(final String search) {
    List<RawUserAuthFlat> usersWithAuthFlat = userRepository.getUserWithAuth(currentUser().getId());
    RawUserAuth userWithAuth = rawUserAuthMapper.toRawUserAuth(usersWithAuthFlat);
    return engineService.search(userWithAuth, search, null);
  }

  private WidgetContext getWidgetContext(String widgetId, Map<String, String> parameters) {
    if (parameters == null) {
      parameters = Map.of();
    }
    Widget widget = widgetService.widget(widgetId);
    CustomDashboard dashboard = widget.getCustomDashboard();
    Map<String, CustomDashboardParameters> defParams = dashboard.toParametersMap();
    List<RawUserAuthFlat> usersWithAuthFlat = userRepository.getUserWithAuth(currentUser().getId());
    RawUserAuth userWithAuth = rawUserAuthMapper.toRawUserAuth(usersWithAuthFlat);
    return new WidgetContext(widget, parameters, defParams, userWithAuth);
  }

  private record WidgetContext(
      Widget widget,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters,
      RawUserAuth user) {}
}
