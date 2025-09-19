package io.openbas.rest.custom_dashboard;

import static io.openbas.helper.StreamHelper.fromIterable;

import io.openbas.database.model.CustomDashboard;
import io.openbas.database.model.Widget;
import io.openbas.database.repository.CustomDashboardRepository;
import io.openbas.database.repository.WidgetRepository;
import io.openbas.engine.api.*;
import io.openbas.rest.custom_dashboard.utils.WidgetUtils;
import io.openbas.utils.CustomDashboardTimeRange;
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

  public ListConfiguration convertWidgetToListConfigurationWithFilterValue(
      Widget widget, Integer seriesIndex, String filterValue) {

    ListConfiguration listConfig = new ListConfiguration();

    WidgetConfiguration widgetConfig = widget.getWidgetConfiguration();
    listConfig.setDateAttribute(widgetConfig.getDateAttribute());
    listConfig.setTimeRange(widgetConfig.getTimeRange());

    WidgetConfiguration.Series series = widgetConfig.getSeries().get(seriesIndex);
    String baseEntity = WidgetUtils.getBaseEntityFilterValue(series.getFilter());
    listConfig.setColumns(WidgetUtils.getColumnsFromBaseEntityName(baseEntity));

    // Add sort
    listConfig.setSorts(createDefaultSort(widgetConfig.getDateAttribute()));

    ListConfiguration.ListPerspective perspectives = new ListConfiguration.ListPerspective();
    perspectives.setName(series.getName());
    perspectives.setFilter(series.getFilter());

    // IT's also used for flat widget config
    if (!WidgetConfigurationType.TEMPORAL_HISTOGRAM.type.equals(
        widgetConfig.getConfigurationType().type)) {
      listConfig.setTimeRange(widgetConfig.getTimeRange());
    }

    if (WidgetConfigurationType.STRUCTURAL_HISTOGRAM.type.equals(
            widgetConfig.getConfigurationType().type)
        && filterValue != null
        && !filterValue.isEmpty()) {
      StructuralHistogramWidget structuralHistogramWidgetConfig =
          (StructuralHistogramWidget) widgetConfig;
      WidgetUtils.setOrAddFilterByKey(
          perspectives.getFilter(), structuralHistogramWidgetConfig.getField(), filterValue);

    } else if (WidgetConfigurationType.TEMPORAL_HISTOGRAM.type.equals(
            widgetConfig.getConfigurationType().type)
        && filterValue != null
        && !filterValue.isEmpty()) {
      listConfig.setTimeRange(CustomDashboardTimeRange.CUSTOM);

      DateHistogramWidget dateWidgetConfig = (DateHistogramWidget) widgetConfig;
      listConfig.setStart(filterValue);
      listConfig.setEnd(WidgetUtils.calcEndDate(filterValue, dateWidgetConfig.getInterval()));
    }

    listConfig.setPerspective(perspectives);

    return listConfig;
  }
}
