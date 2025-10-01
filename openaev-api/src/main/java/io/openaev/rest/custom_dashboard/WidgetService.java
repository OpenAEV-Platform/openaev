package io.openaev.rest.custom_dashboard;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Filters;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.Widget;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.database.repository.WidgetRepository;
import io.openaev.engine.EngineService;
import io.openaev.engine.api.*;
import io.openaev.rest.custom_dashboard.utils.WidgetUtils;
import io.openaev.utils.CustomDashboardTimeRange;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WidgetService {

  private final CustomDashboardRepository customDashboardRepository;
  private final WidgetRepository widgetRepository;
  private final EngineService esService;

  // -- CRUD --

  @Transactional
  public Widget createWidget(
      @NotBlank final String customDashboardId, @NotNull final Widget widget) {
    // FIXME: needs some refactoring
    // -> CustomDashboardRepository should not be called directly here but using the service here is
    // causing circular dependency
    CustomDashboard customDashboard =
        customDashboardRepository
            .findById(customDashboardId)
            .orElseThrow(
                () ->
                    new EntityNotFoundException(
                        "Custom dashboard not found with id: " + customDashboardId));
    widget.setCustomDashboard(customDashboard);
    return this.widgetRepository.save(widget);
  }

  @Transactional(readOnly = true)
  public List<Widget> widgets(@NotBlank final String customDashboardId) {
    return fromIterable(this.widgetRepository.findAllByCustomDashboardId(customDashboardId));
  }

  @Transactional(readOnly = true)
  public Widget widget(@NotBlank final String customDashboardId, @NotBlank final String widgetId) {
    return this.widgetRepository
        .findByCustomDashboardIdAndId(customDashboardId, widgetId)
        .orElseThrow(() -> new EntityNotFoundException("Widget with id: " + widgetId));
  }

  @Transactional(readOnly = true)
  public Widget widget(@NotBlank final String widgetId) {
    return this.widgetRepository
        .findById(widgetId)
        .orElseThrow(() -> new EntityNotFoundException("Widget with id: " + widgetId));
  }

  @Transactional
  public Widget updateWidget(@NotNull final Widget widget) {
    return this.widgetRepository.save(widget);
  }

  @Transactional
  public void deleteWidget(
      @NotBlank final String customDashboardId, @NotBlank final String widgetId) {
    if (!this.widgetRepository.existsWidgetByCustomDashboardIdAndId(customDashboardId, widgetId)) {
      throw new EntityNotFoundException("Widget not found with id: " + widgetId);
    }
    this.widgetRepository.deleteById(widgetId);
  }

  private List<EngineSortField> createDefaultSort(String dateAttribute) {
    EngineSortField sort = new EngineSortField();
    sort.setFieldName(dateAttribute);
    return List.of(sort);
  }

  /**
   * Converts a widget configuration to a list configuration for data display. Applies
   * series-specific filters and handles different widget types (temporal/structural histograms).
   *
   * @param widget the source widget containing the configuration to convert
   * @param seriesIndex the index of the series within the widget to use for conversion
   * @param filterValues optional filter values to apply (e.g., date ranges for temporal, field
   *     values for structural histograms)
   * @return a ListConfiguration object configured based on the widget settings
   */
  public ListConfiguration convertWidgetToListConfiguration(
      Widget widget, Integer seriesIndex, List<String> filterValues) {

    WidgetConfiguration widgetConfig = widget.getWidgetConfiguration();
    WidgetConfiguration.Series series = widgetConfig.getSeries().get(seriesIndex);
    String baseEntity = WidgetUtils.getBaseEntityFilterValue(series.getFilter());

    ListConfiguration listConfig = new ListConfiguration();
    listConfig.setTimeRange(widgetConfig.getTimeRange());
    listConfig.setDateAttribute(widgetConfig.getDateAttribute());
    listConfig.setColumns(WidgetUtils.getColumnsFromBaseEntityName(baseEntity));
    listConfig.setSorts(createDefaultSort(widgetConfig.getDateAttribute()));

    ListConfiguration.ListPerspective perspectives = new ListConfiguration.ListPerspective();
    perspectives.setName(series.getName());
    perspectives.setFilter(series.getFilter());

    if (WidgetConfigurationType.STRUCTURAL_HISTOGRAM.type.equals(
            widgetConfig.getConfigurationType().type)
        && filterValues != null
        && !filterValues.isEmpty()) {
      StructuralHistogramWidget structuralHistogramWidgetConfig =
          (StructuralHistogramWidget) widgetConfig;
      WidgetUtils.setOrAddFilterByKey(
          perspectives.getFilter(),
          structuralHistogramWidgetConfig.getField(),
          filterValues,
          Filters.FilterOperator.contains);

    } else if (WidgetConfigurationType.TEMPORAL_HISTOGRAM.type.equals(
            widgetConfig.getConfigurationType().type)
        && filterValues != null
        && !filterValues.isEmpty()) {
      listConfig.setTimeRange(CustomDashboardTimeRange.CUSTOM);
      DateHistogramWidget dateWidgetConfig = (DateHistogramWidget) widgetConfig;
      listConfig.setStart(filterValues.getFirst());
      listConfig.setEnd(
          WidgetUtils.calcEndDate(filterValues.getFirst(), dateWidgetConfig.getInterval()));
    }

    listConfig.setPerspective(perspectives);
    return listConfig;
  }

  /**
   * Converts a security coverage widget configuration to a list configuration
   *
   * @param widget the source widget containing the configuration to convert
   * @param attackPatternIds attackPatternIds list of attack pattern IDs to filter by
   * @return a ListConfiguration object configured based on the widget settings
   */
  public ListConfiguration convertSecurityCoverageWidgetToListConfiguration(
      Widget widget, List<String> attackPatternIds) {
    ListConfiguration listInjectExpectationsConfig =
        this.convertWidgetToListConfiguration(widget, 0, attackPatternIds);
    List<String> statusFilters =
        List.of(
            InjectExpectation.EXPECTATION_STATUS.FAILED.name(),
            InjectExpectation.EXPECTATION_STATUS.SUCCESS.name());
    WidgetUtils.setOrAddFilterByKey(
        listInjectExpectationsConfig.getPerspective().getFilter(),
        "inject_expectation_status",
        statusFilters,
        Filters.FilterOperator.contains);
    return listInjectExpectationsConfig;
  }
}
