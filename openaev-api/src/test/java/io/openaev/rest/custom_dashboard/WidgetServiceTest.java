package io.openaev.rest.custom_dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Widget;
import io.openaev.database.model.WidgetLayout;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.StructuralHistogramWidget;
import io.openaev.engine.api.WidgetConfigurationWithSeries;
import io.openaev.engine.api.WidgetType;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.fixtures.WidgetFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WidgetService unit tests")
class WidgetServiceTest {

  // Conversion methods only transform the widget configuration; they never touch the
  // repositories or the metric collector, so the service is instantiated bare.
  private final WidgetService widgetService = new WidgetService(null, null, null);

  private static final String SCENARIO_ID = "scenario-id";
  private static final String ATTACK_PATTERN_ID = "attack-pattern-id";

  /**
   * Mirrors the synthetic contextual widgets the overview pages build for their kill chain and
   * posture drill-downs: a security-coverage widget declaring a single series that carries the
   * whole scope (base_entity plus the scenario/simulation side filter).
   */
  private static Widget createSingleSeriesSecurityCoverageWidget() {
    Widget widget = new Widget();
    widget.setType(WidgetType.SECURITY_COVERAGE_CHART);
    StructuralHistogramWidget widgetConfig = new StructuralHistogramWidget();
    WidgetConfigurationWithSeries.Series series = new WidgetConfigurationWithSeries.Series();
    series.setName("");
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    filterGroup.setFilters(
        new ArrayList<>(
            List.of(
                createFilter("base_entity", List.of("expectation-inject")),
                createFilter("base_scenario_side", List.of(SCENARIO_ID)))));
    series.setFilter(filterGroup);
    widgetConfig.setSeries(List.of(series));
    widgetConfig.setTitle("Kill chain results");
    widgetConfig.setField("base_attack_patterns_side");
    widgetConfig.setDateAttribute("base_created_at");
    widgetConfig.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    widget.setWidgetConfiguration(widgetConfig);
    widget.setLayout(new WidgetLayout());
    return widget;
  }

  private static Filters.Filter createFilter(String key, List<String> values) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setMode(Filters.FilterMode.or);
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setValues(values);
    return filter;
  }

  private static Optional<Filters.Filter> findFilter(Filters.FilterGroup group, String key) {
    return group.getFilters().stream().filter(f -> key.equals(f.getKey())).findFirst();
  }

  @Test
  @DisplayName(
      "Security coverage drill-down succeeds on a widget declaring a single series and keeps its"
          + " scope")
  void givenSingleSeriesSecurityCoverageWidget_whenConverting_thenPerspectiveCarriesSeriesScope() {
    Widget widget = createSingleSeriesSecurityCoverageWidget();

    ListConfiguration listConfiguration =
        widgetService.convertSecurityCoverageWidgetToListConfiguration(
            widget, Map.of("base_attack_patterns_side", List.of(ATTACK_PATTERN_ID)));

    Filters.FilterGroup perspectiveFilter = listConfiguration.getPerspective().getFilter();
    assertThat(findFilter(perspectiveFilter, "base_entity"))
        .hasValueSatisfying(
            filter -> assertThat(filter.getValues()).containsExactly("expectation-inject"));
    assertThat(findFilter(perspectiveFilter, "base_scenario_side"))
        .hasValueSatisfying(filter -> assertThat(filter.getValues()).containsExactly(SCENARIO_ID));
    assertThat(findFilter(perspectiveFilter, "base_attack_patterns_side"))
        .hasValueSatisfying(
            filter -> assertThat(filter.getValues()).containsExactly(ATTACK_PATTERN_ID));
  }

  @Test
  @DisplayName(
      "Security coverage drill-down unions the status values of a two-series widget (#7079)")
  void givenTwoSeriesSecurityCoverageWidget_whenConverting_thenPerspectiveUnionsStatusValues() {
    Widget widget =
        WidgetFixture.createSecurityConverageWidget(
            CustomDashboardTimeRange.ALL_TIME,
            "base_created_at",
            BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION);

    ListConfiguration listConfiguration =
        widgetService.convertSecurityCoverageWidgetToListConfiguration(
            widget, Map.of("base_attack_patterns_side", List.of(ATTACK_PATTERN_ID)));

    Filters.FilterGroup perspectiveFilter = listConfiguration.getPerspective().getFilter();
    assertThat(findFilter(perspectiveFilter, "inject_expectation_status"))
        .hasValueSatisfying(
            filter ->
                assertThat(filter.getValues())
                    .containsExactlyInAnyOrder(
                        BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS.name(),
                        BaseInjectExpectation.EXPECTATION_STATUS.FAILED.name()));
    assertThat(findFilter(perspectiveFilter, "base_entity"))
        .hasValueSatisfying(
            filter -> assertThat(filter.getValues()).containsExactly("expectation-inject"));
  }
}
