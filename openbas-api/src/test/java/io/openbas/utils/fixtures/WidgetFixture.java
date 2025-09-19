package io.openbas.utils.fixtures;

import static io.openbas.engine.api.WidgetType.DONUT;
import static io.openbas.engine.api.WidgetType.VERTICAL_BAR_CHART;

import io.openbas.database.model.Filters;
import io.openbas.database.model.Widget;
import io.openbas.database.model.WidgetLayout;
import io.openbas.engine.api.*;
import io.openbas.utils.CustomDashboardTimeRange;
import java.util.ArrayList;
import java.util.List;

public class WidgetFixture {

  public static final String NAME = "Widget 1";

  public static Widget createDefaultWidget() {
    Widget widget = new Widget();
    widget.setType(VERTICAL_BAR_CHART);
    DateHistogramWidget widgetConfig = new DateHistogramWidget();
    widgetConfig.setTitle(NAME);
    widgetConfig.setDateAttribute("base_updated_at");
    widgetConfig.setTimeRange(CustomDashboardTimeRange.LAST_QUARTER);
    widgetConfig.setSeries(new ArrayList<>());
    widgetConfig.setInterval(HistogramInterval.day);
    widgetConfig.setStart("2012-12-21T10:45:23Z");
    widgetConfig.setEnd("2012-12-22T10:45:23Z");
    widget.setWidgetConfiguration(widgetConfig);
    WidgetLayout widgetLayout = new WidgetLayout();
    widget.setLayout(widgetLayout);
    return widget;
  }

  public static Widget creatTemporalWidgetWithTimeRange(
      CustomDashboardTimeRange timeRange,
      String dateAttribute,
      HistogramInterval interval,
      String entityName) {
    Widget widget = new Widget();
    widget.setType(VERTICAL_BAR_CHART);
    // series
    DateHistogramWidget widgetConfig = new DateHistogramWidget();
    WidgetConfiguration.Series series = new WidgetConfiguration.Series();
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    Filters.Filter filter = new Filters.Filter();
    filter.setKey("base_entity");
    filter.setMode(Filters.FilterMode.or);
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setValues(List.of(entityName));
    filterGroup.setFilters(List.of(filter));
    series.setFilter(filterGroup);
    // basic configuration
    widgetConfig.setSeries(List.of(series));
    widgetConfig.setTitle(NAME);
    widgetConfig.setDateAttribute(dateAttribute);
    widgetConfig.setInterval(interval);
    widgetConfig.setTimeRange(timeRange);
    widget.setWidgetConfiguration(widgetConfig);
    WidgetLayout widgetLayout = new WidgetLayout();
    widget.setLayout(widgetLayout);
    return widget;
  }

  public static Widget creatStructuralWidgetWithTimeRange(
      CustomDashboardTimeRange timeRange, String dateAttribute, String field, String entityName) {
    Widget widget = new Widget();
    widget.setType(DONUT);
    // series
    StructuralHistogramWidget widgetConfig = new StructuralHistogramWidget();
    WidgetConfiguration.Series series = new WidgetConfiguration.Series();
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    Filters.Filter filter = new Filters.Filter();
    filter.setKey("base_entity");
    filter.setMode(Filters.FilterMode.or);
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setValues(List.of(entityName));
    filterGroup.setFilters(List.of(filter));
    series.setFilter(filterGroup);
    // basic configuration
    widgetConfig.setSeries(List.of(series));
    widgetConfig.setTitle(NAME);
    widgetConfig.setField(field);
    widgetConfig.setDateAttribute(dateAttribute);
    widgetConfig.setTimeRange(timeRange);
    widget.setWidgetConfiguration(widgetConfig);
    WidgetLayout widgetLayout = new WidgetLayout();
    widget.setLayout(widgetLayout);
    return widget;
  }

  public static Widget createNumberWidgetWithEntity(String entityName) {
    Widget widget = new Widget();
    widget.setType(WidgetType.NUMBER);
    // series
    WidgetConfiguration.Series series = new WidgetConfiguration.Series();
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    Filters.Filter filter = new Filters.Filter();
    filter.setValues(List.of(entityName));
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setMode(Filters.FilterMode.and);
    filter.setKey("base_entity");
    filterGroup.setFilters(List.of(filter));
    series.setFilter(filterGroup);
    // basic configuration
    FlatConfiguration flatConfiguration = new FlatConfiguration();
    flatConfiguration.setSeries(List.of(series));
    flatConfiguration.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    flatConfiguration.setDateAttribute("base_created_at");
    widget.setWidgetConfiguration(flatConfiguration);
    // basic layout
    widget.setLayout(new WidgetLayout());
    return widget;
  }

  public static Widget createNumberWidgetWithEndpointAndFilter() {
    Widget widget = new Widget();
    widget.setType(WidgetType.NUMBER);
    // series
    WidgetConfiguration.Series series = new WidgetConfiguration.Series();
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    List<Filters.Filter> filters = new ArrayList<>();
    Filters.Filter filter = new Filters.Filter();
    filter.setValues(List.of("endpoint"));
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setMode(Filters.FilterMode.or);
    filter.setKey("base_entity");
    filters.add(filter);
    filter.setValues(List.of("Windows"));
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setMode(Filters.FilterMode.or);
    filter.setKey("endpoint_platform");
    filters.add(filter);
    filterGroup.setFilters(filters);
    series.setFilter(filterGroup);
    // basic configuration
    FlatConfiguration flatConfiguration = new FlatConfiguration();
    flatConfiguration.setSeries(List.of(series));
    flatConfiguration.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    flatConfiguration.setDateAttribute("base_created_at");
    widget.setWidgetConfiguration(flatConfiguration);
    // basic layout
    widget.setLayout(new WidgetLayout());
    return widget;
  }

  public static Widget createNumberWidgetWithEntityAndTimeRange(
      String entityName, CustomDashboardTimeRange timeRange, String dateAttribute) {
    Widget widget = new Widget();
    widget.setType(WidgetType.NUMBER);
    // series
    WidgetConfiguration.Series series = new WidgetConfiguration.Series();
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    Filters.Filter filter = new Filters.Filter();
    filter.setValues(List.of(entityName));
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setMode(Filters.FilterMode.or);
    filter.setKey("base_entity");
    filterGroup.setFilters(List.of(filter));
    series.setFilter(filterGroup);
    // basic configuration
    FlatConfiguration flatConfiguration = new FlatConfiguration();
    flatConfiguration.setSeries(List.of(series));
    flatConfiguration.setDateAttribute(dateAttribute);
    flatConfiguration.setTimeRange(timeRange);
    widget.setWidgetConfiguration(flatConfiguration);
    // basic layout
    widget.setLayout(new WidgetLayout());
    return widget;
  }

  public static Widget createListWidgetWithEntity(String entityName) {
    Widget widget = new Widget();
    widget.setType(WidgetType.LIST);
    // series
    ListConfiguration.ListPerspective series = new ListConfiguration.ListPerspective();
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    Filters.Filter filter = new Filters.Filter();
    filter.setValues(List.of(entityName));
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setMode(Filters.FilterMode.and);
    filter.setKey("base_entity");
    filterGroup.setFilters(List.of(filter));
    series.setFilter(filterGroup);
    // basic configuration
    ListConfiguration listConfiguration = new ListConfiguration();
    listConfiguration.setPerspective(series);
    widget.setWidgetConfiguration(listConfiguration);
    // basic layout
    widget.setLayout(new WidgetLayout());
    return widget;
  }
}
