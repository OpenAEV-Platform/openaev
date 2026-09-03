package io.openaev.rest.dashboard;

import static io.openaev.config.SessionHelper.currentUser;

import io.openaev.database.model.*;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.database.raw.RawUserAuthFlat;
import io.openaev.database.repository.UserRepository;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.api.*;
import io.openaev.engine.model.EsSearch;
import io.openaev.engine.query.*;
import io.openaev.rest.custom_dashboard.WidgetService;
import io.openaev.service.EsAttackPathService;
import io.openaev.service.EsSecurityDomainService;
import io.openaev.utils.es.WidgetToEntitiesInput;
import io.openaev.utils.es.WidgetToEntitiesOutput;
import io.openaev.utils.mapper.RawUserAuthMapper;
import io.openaev.utils.pagination.Pagination;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class DashboardService {

  /**
   * High terms-bucket cap for security coverage widgets so every attack pattern is returned. The
   * regular widget limit (often the 100 default) silently truncates the per-series buckets on busy
   * platforms, making coverage tiles show "perfect" scores while failures exist outside the top-N
   * buckets. Mirrors {@code AttackPatternService.COVERAGE_BUCKET_CAP}.
   */
  private static final int COVERAGE_BUCKET_CAP = 10_000;

  private final EsAttackPathService esAttackPathService;
  private final EngineService engineService;
  private final UserRepository userRepository;
  private final WidgetService widgetService;
  private final EsSecurityDomainService esSecurityDomainService;

  private final RawUserAuthMapper rawUserAuthMapper;

  /**
   * Retrieves count data from Elasticsearch for a specific widget based on its configuration.
   *
   * @param widgetId the id from the {@link Widget} defining the type and configuration
   * @param parameters parameters passed at runtime (e.g. filters, date ranges)
   * @return EsCountInterval a count object, including the current and previous interval count and
   *     the difference between the two
   */
  public EsCountInterval count(String widgetId, Map<String, String> parameters) {
    WidgetContext widgetContext = getWidgetContext(widgetId, parameters);
    FlatConfiguration config = (FlatConfiguration) widgetContext.widget().getWidgetConfiguration();
    CountRuntime runtime =
        new CountRuntime(config, widgetContext.parameters(), widgetContext.definitionParameters());
    return engineService.count(widgetContext.user(), runtime);
  }

  public EsAvgs average(String widgetId, Map<String, String> parameters) {
    WidgetContext widgetContext = getWidgetContext(widgetId, parameters);
    AverageConfiguration config =
        (AverageConfiguration) widgetContext.widget().getWidgetConfiguration();
    AverageRuntime runtime =
        new AverageRuntime(
            esSecurityDomainService.setFieldsForQuery(config),
            widgetContext.parameters(),
            widgetContext.definitionParameters());
    return engineService.average(widgetContext.user(), runtime);
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
      if (isSecurityCoverageWidget(widgetContext.widget())) {
        // Persisted coverage widgets may carry a small stored limit: clamp it up so no
        // attack pattern bucket is ever truncated (tile counts must match the drill-down).
        config.setLimit(Math.max(config.getLimit(), COVERAGE_BUCKET_CAP));
      }
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
   * @param pagination pagination passed at runtime
   * @return a list of entities retrieved from the engine service
   */
  private EsEntities executeListQuery(
      WidgetContext widgetContext, ListConfiguration config, @Nullable Pagination pagination) {
    ListRuntime runtime =
        new ListRuntime(
            config, widgetContext.parameters(), widgetContext.definitionParameters(), pagination);
    return engineService.entities(widgetContext.user(), runtime);
  }

  /**
   * Retrieves a list of entities from Elasticsearch for a widget configured as a list.
   *
   * @param widgetId the id from the {@link Widget} with a list configuration
   * @param parameters parameters passed at runtime (e.g. filters)
   * @param pagination pagination passed at runtime
   * @return list of entities matching the list widget query
   */
  public EsEntities entities(
      String widgetId, Map<String, String> parameters, @Nullable Pagination pagination) {
    WidgetContext widgetContext = getWidgetContext(widgetId, parameters);
    ListConfiguration config = (ListConfiguration) widgetContext.widget().getWidgetConfiguration();
    return executeListQuery(widgetContext, config, pagination);
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
    EsEntities datas;

    if (isSecurityCoverageWidget(widgetContext.widget)) {
      listConfig =
          widgetService.convertSecurityCoverageWidgetToListConfiguration(
              widgetContext.widget, input.getFilterValuesMap());
    } else {
      listConfig =
          widgetService.convertWidgetToListConfiguration(
              widgetContext.widget, input.resolvedSeriesIndexes(), input.getFilterValuesMap());
    }

    datas = executeListQuery(widgetContext, listConfig, input.getPagination());
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

  // -- AD-HOC (non-persisted) WIDGET QUERIES --
  // Used by hardcoded platform dashboards: the full widget configuration is
  // provided by the caller instead of referencing a stored widget.

  /**
   * Retrieves series data for an ad-hoc widget configuration (histograms).
   *
   * @param configuration the widget configuration (temporal or structural histogram)
   * @param parameters parameters passed at runtime
   * @return list of {@link EsSeries} suitable for charting
   */
  public List<EsSeries> adHocSeries(
      WidgetConfiguration configuration, Map<String, String> parameters) {
    RawUserAuth user = currentUserAuth();
    Map<String, String> params = parameters == null ? Map.of() : parameters;
    Map<String, CustomDashboardParameters> defParams = adHocDefinitionParameters();
    if (configuration instanceof DateHistogramWidget config) {
      return engineService.multiDateHistogram(
          user, new DateHistogramRuntime(config, params, defParams));
    } else if (configuration instanceof StructuralHistogramWidget config) {
      return engineService.multiTermHistogram(
          user, new StructuralHistogramRuntime(config, params, defParams));
    }
    throw new UnsupportedOperationException(
        "Unsupported ad-hoc widget configuration: " + configuration.getConfigurationType());
  }

  /**
   * Retrieves security-domain averages for an ad-hoc average widget configuration.
   *
   * @param configuration the average widget configuration
   * @param parameters parameters passed at runtime
   * @return the security domain averages
   */
  public EsAvgs adHocAverage(WidgetConfiguration configuration, Map<String, String> parameters) {
    if (!(configuration instanceof AverageConfiguration config)) {
      throw new UnsupportedOperationException(
          "Unsupported ad-hoc widget configuration: " + configuration.getConfigurationType());
    }
    RawUserAuth user = currentUserAuth();
    Map<String, String> params = parameters == null ? Map.of() : parameters;
    return engineService.average(
        user,
        new AverageRuntime(
            esSecurityDomainService.setFieldsForQuery(config),
            params,
            adHocDefinitionParameters()));
  }

  /**
   * Retrieves count data for an ad-hoc flat widget configuration.
   *
   * @param configuration the flat widget configuration
   * @param parameters parameters passed at runtime
   * @return the count with previous interval comparison
   */
  public EsCountInterval adHocCount(
      WidgetConfiguration configuration, Map<String, String> parameters) {
    if (!(configuration instanceof FlatConfiguration config)) {
      throw new UnsupportedOperationException(
          "Unsupported ad-hoc widget configuration: " + configuration.getConfigurationType());
    }
    RawUserAuth user = currentUserAuth();
    Map<String, String> params = parameters == null ? Map.of() : parameters;
    return engineService.count(user, new CountRuntime(config, params, adHocDefinitionParameters()));
  }

  /**
   * Retrieves entities for an ad-hoc list widget configuration.
   *
   * @param configuration the list widget configuration
   * @param parameters parameters passed at runtime
   * @param pagination pagination passed at runtime
   * @return the entities matching the list query
   */
  public EsEntities adHocEntities(
      WidgetConfiguration configuration,
      Map<String, String> parameters,
      @Nullable Pagination pagination) {
    if (!(configuration instanceof ListConfiguration config)) {
      throw new UnsupportedOperationException(
          "Unsupported ad-hoc widget configuration: " + configuration.getConfigurationType());
    }
    RawUserAuth user = currentUserAuth();
    Map<String, String> params = parameters == null ? Map.of() : parameters;
    return engineService.entities(
        user, new ListRuntime(config, params, adHocDefinitionParameters(), pagination));
  }

  /**
   * Converts an ad-hoc (non-persisted) widget into a scoped entity list. Mirrors {@link
   * #widgetToEntitiesRuntime} but builds a transient widget from the provided type and
   * configuration instead of loading a stored one, so the built-in default dashboard drill-downs
   * behave exactly like custom dashboard ones.
   *
   * @param widgetType the widget type (drives the security-coverage special case)
   * @param configuration the full widget configuration
   * @param input clicked filter values, series index, parameters and pagination
   * @return output containing both the generated list configuration and retrieved entities
   */
  public WidgetToEntitiesOutput adHocEntitiesRuntime(
      WidgetType widgetType, WidgetConfiguration configuration, WidgetToEntitiesInput input) {
    Widget transientWidget = new Widget();
    transientWidget.setType(widgetType);
    transientWidget.setWidgetConfiguration(configuration);

    ListConfiguration listConfig;
    if (isSecurityCoverageWidget(transientWidget)) {
      listConfig =
          widgetService.convertSecurityCoverageWidgetToListConfiguration(
              transientWidget, input.getFilterValuesMap());
    } else {
      listConfig =
          widgetService.convertWidgetToListConfiguration(
              transientWidget, input.resolvedSeriesIndexes(), input.getFilterValuesMap());
    }

    Map<String, String> params = input.getParameters() == null ? Map.of() : input.getParameters();
    EsEntities datas =
        engineService.entities(
            currentUserAuth(),
            new ListRuntime(
                listConfig, params, adHocDefinitionParameters(), input.getPagination()));
    return WidgetToEntitiesOutput.builder().listConfiguration(listConfig).esEntities(datas).build();
  }

  private RawUserAuth currentUserAuth() {
    List<RawUserAuthFlat> usersWithAuthFlat = userRepository.getUserWithAuth(currentUser().getId());
    return rawUserAuthMapper.toRawUserAuth(usersWithAuthFlat);
  }

  /**
   * The engine date-range helpers always resolve the dashboard timeRange / startDate / endDate
   * parameter definitions, even when the widget carries an explicit time range. Ad-hoc widgets have
   * no persisted dashboard, so we provide synthetic (never persisted) definitions; without matching
   * runtime values the engine falls back to the widget configuration time range.
   */
  private Map<String, CustomDashboardParameters> adHocDefinitionParameters() {
    Map<String, CustomDashboardParameters> definitions = new HashMap<>();
    definitions.put(
        "_adhoc_time_range",
        adHocParameter(
            "_adhoc_time_range",
            "Time range",
            CustomDashboardParameters.CustomDashboardParameterType.timeRange));
    definitions.put(
        "_adhoc_start_date",
        adHocParameter(
            "_adhoc_start_date",
            "Start date",
            CustomDashboardParameters.CustomDashboardParameterType.startDate));
    definitions.put(
        "_adhoc_end_date",
        adHocParameter(
            "_adhoc_end_date",
            "End date",
            CustomDashboardParameters.CustomDashboardParameterType.endDate));
    return definitions;
  }

  private CustomDashboardParameters adHocParameter(
      String id, String name, CustomDashboardParameters.CustomDashboardParameterType type) {
    CustomDashboardParameters parameter = new CustomDashboardParameters();
    parameter.setId(id);
    parameter.setName(name);
    parameter.setType(type);
    return parameter;
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
