package io.openaev.rest.custom_dashboard;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.context.TenantContext;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Widget;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.database.repository.WidgetRepository;
import io.openaev.engine.api.*;
import io.openaev.rest.custom_dashboard.utils.WidgetUtils;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.CustomDashboardTimeRange;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WidgetService {

  private final CustomDashboardRepository customDashboardRepository;
  private final WidgetRepository widgetRepository;
  private final ActionMetricCollector actionMetricCollector;

  // -- CRUD --

  @Transactional
  public Widget createWidget(
      @NotBlank final String customDashboardId, @NotNull final Widget widget) {
    // FIXME: needs some refactoring
    // -> CustomDashboardRepository should not be called directly here but using the service here is
    // causing circular dependency
    CustomDashboard customDashboard =
        customDashboardRepository
            .findByIdAndTenantId(customDashboardId, TenantContext.getCurrentTenant())
            .orElseThrow(
                () ->
                    new EntityNotFoundException(
                        "Custom dashboard not found with id: " + customDashboardId));
    widget.setCustomDashboard(customDashboard);
    this.sendTelemetryEvent(widget, false);
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
        .findByIdAndTenantId(widgetId, TenantContext.getCurrentTenant())
        .orElseThrow(() -> new EntityNotFoundException("Widget with id: " + widgetId));
  }

  @Transactional
  public Widget updateWidget(@NotNull final Widget widget) {
    return this.widgetRepository.save(widget);
  }

  @Transactional
  public void deleteWidget(
      @NotBlank final String customDashboardId, @NotBlank final String widgetId) {
    Optional<Widget> widget =
        this.widgetRepository.findByCustomDashboardIdAndId(customDashboardId, widgetId);
    if (widget.isEmpty()) {
      throw new EntityNotFoundException("Widget not found with id: " + widgetId);
    }
    this.sendTelemetryEvent(widget.get(), true);
    this.widgetRepository.deleteById(widgetId);
  }

  private List<EngineSortField> createDefaultSort(String dateAttribute) {
    EngineSortField sort = new EngineSortField();
    sort.setFieldName(dateAttribute);
    // Drill-down lists sort on a date attribute: most recent first.
    sort.setDirection(SortDirection.DESC);
    return List.of(sort);
  }

  /**
   * Converts a widget configuration to a list configuration for data display, scoped to every
   * series that produced the clicked number.
   *
   * <p>A tile whose value spans several series (an "attempted" total built from SUCCESS + FAILED +
   * PENDING series, say) cannot describe its own scope with a single series index. Passing every
   * contributing index here makes the drilled list resolve to exactly the documents that were
   * counted, instead of leaving the caller to hand-rebuild the scope through {@code filterValues} -
   * a reconstruction that silently drifts from the widget definition as soon as one of the two
   * changes.
   *
   * @param widget the source widget containing the configuration to convert
   * @param seriesIndexes the indexes of the series to OR together; every index must name a series
   *     the widget declares, else the drill-down is rejected
   * @param filterValues optional filter values to apply (e.g., date ranges for temporal, field
   *     values for structural histograms)
   * @return a ListConfiguration object configured based on the widget settings
   */
  public ListConfiguration convertWidgetToListConfiguration(
      Widget widget, List<Integer> seriesIndexes, Map<String, List<String>> filterValues) {

    WidgetConfiguration widgetConfig = widget.getWidgetConfiguration();
    WidgetConfigurationWithSeries.Series series = mergeSeries(widgetConfig, seriesIndexes);

    String baseEntity = WidgetUtils.getBaseEntityFilterValue(series.getFilter());

    ListConfiguration listConfig = new ListConfiguration();
    listConfig.setTimeRange(widgetConfig.getTimeRange());
    listConfig.setDateAttribute(widgetConfig.getDateAttribute());
    listConfig.setColumns(WidgetUtils.getColumnsFromBaseEntityName(baseEntity));
    listConfig.setSorts(createDefaultSort(widgetConfig.getDateAttribute()));

    ListConfiguration.ListPerspective perspectives = new ListConfiguration.ListPerspective();
    perspectives.setName(series.getName());
    perspectives.setFilter(series.getFilter());

    if ((WidgetConfigurationType.STRUCTURAL_HISTOGRAM.type.equals(
                widgetConfig.getConfigurationType().type)
            || WidgetConfigurationType.AVERAGE.type.equals(
                widgetConfig.getConfigurationType().type))
        && filterValues != null
        && !filterValues.isEmpty()) {
      filterValues.forEach(
          (key, values) -> {
            // Drill-down values are exact aggregation bucket keys (enum / keyword
            // terms), so the filter must use an exact-match operator. "contains"
            // is not a valid operator for these keyword fields and surfaces as an
            // unselectable operator in the filter chip UI.
            WidgetUtils.setOrAddFilterByKey(
                perspectives.getFilter(), key, values, Filters.FilterOperator.eq);
          });
    } else if (WidgetConfigurationType.TEMPORAL_HISTOGRAM.type.equals(
            widgetConfig.getConfigurationType().type)
        && filterValues != null
        && !filterValues.isEmpty()) {
      listConfig.setTimeRange(CustomDashboardTimeRange.CUSTOM);
      DateHistogramWidget dateWidgetConfig = (DateHistogramWidget) widgetConfig;
      Map.Entry<String, List<String>> entry = filterValues.entrySet().iterator().next();
      listConfig.setStart(entry.getValue().getFirst());
      listConfig.setEnd(
          WidgetUtils.calcEndDate(entry.getValue().getFirst(), dateWidgetConfig.getInterval()));
    }

    listConfig.setPerspective(perspectives);
    return listConfig;
  }

  /**
   * Builds the single series the drill-down runs against, ORing the filters of every selected
   * series into one flat group.
   *
   * <p>{@link Filters.FilterGroup} carries a flat list of filters with no nesting, so a literal
   * {@code (E AND s=X) OR (E AND s=Y)} cannot be represented. It does not need to be: the series of
   * one widget differ by a single discriminating key (the status, for SUCCESS / FAILED / PENDING
   * series over the same entity), and for that shape the OR collapses exactly onto {@code E AND s
   * IN (X, Y)} - a per-key union of values. Series that diverge on more than one key have no such
   * collapse and their union would silently be a superset of the real scope, so that case is
   * rejected rather than over-counted. The collapse also presumes both series select documents the
   * same way on the diverging key; {@link #mergeFilter} rejects the shapes where it would not (a
   * different operator, a negated operator, a multi-value and-mode filter).
   *
   * <p>Filters are deep-copied: the returned group is handed to {@link
   * WidgetUtils#setOrAddFilterByKey} which mutates it in place, and the source belongs to the
   * widget configuration (a persisted entity for stored dashboards).
   */
  private WidgetConfigurationWithSeries.Series mergeSeries(
      WidgetConfiguration widgetConfig, List<Integer> seriesIndexes) {
    List<WidgetConfigurationWithSeries.Series> all =
        widgetConfig instanceof WidgetConfigurationWithSeries config && config.getSeries() != null
            ? config.getSeries()
            : List.of();
    List<Integer> indexes = seriesIndexes == null ? List.of() : seriesIndexes;
    // These indexes reach us as client-controlled URL parameters, so an unknown one has to
    // answer 400. Silently dropping it would detach the drilled list from the number the
    // caller believes it is expanding, and resolving to an empty series instead would carry
    // a null filter list (Lombok drops the field initializer under @Builder.Default, which
    // is why Filters null-guards it everywhere) and surface as an opaque 500 further down.
    if (indexes.isEmpty()
        || indexes.stream().anyMatch(index -> index == null || index < 0 || index >= all.size())) {
      throw new IllegalArgumentException(
          "Cannot drill down into series %s: the widget declares %d series"
              .formatted(indexes, all.size()));
    }
    List<WidgetConfigurationWithSeries.Series> selected = indexes.stream().map(all::get).toList();

    WidgetConfigurationWithSeries.Series merged = new WidgetConfigurationWithSeries.Series();
    merged.setName(selected.getFirst().getName());
    merged.setFilter(copyFilterGroup(selected.getFirst().getFilter()));
    if (selected.size() == 1) {
      return merged;
    }

    Set<String> divergingKeys = new HashSet<>();
    for (WidgetConfigurationWithSeries.Series other : selected.subList(1, selected.size())) {
      Filters.FilterGroup otherFilter = copyFilterGroup(other.getFilter());
      if (!filterKeys(merged.getFilter()).equals(filterKeys(otherFilter))) {
        throw new IllegalArgumentException(
            "Cannot drill down into series with different filter keys: %s vs %s"
                .formatted(filterKeys(merged.getFilter()), filterKeys(otherFilter)));
      }
      for (Filters.Filter filter : otherFilter.getFilters()) {
        Filters.Filter target = merged.getFilter().findByKey(filter.getKey()).orElseThrow();
        mergeFilter(target, filter, divergingKeys);
      }
    }
    if (divergingKeys.size() > 1) {
      throw new IllegalArgumentException(
          "Cannot drill down into series diverging on more than one filter key: " + divergingKeys);
    }
    return merged;
  }

  /**
   * Merges one series' filter into the accumulating drill-down filter for the same key.
   *
   * <p>Divergence is a set difference, not union growth: when one series' values are a subset of
   * another's the union does not grow, yet the series do differ on that key. Missing it would let a
   * second diverging key pass the guard in {@link #mergeSeries} and widen the scope to documents
   * matching neither original series.
   *
   * <p>The per-key value union stands in for the OR of the series only when both sides select
   * documents the same way. A different operator makes the merged filter mean something neither
   * series said, so operators must match. Negated operators never union soundly: the engine turns
   * every not_eq / not_contains value into a must-not clause - a conjunction of exclusions - so
   * adding values narrows the match where the OR of the series must widen it. A multi-value
   * and-mode side has no flat collapse either ({@code (A AND B) OR C} cannot be expressed as one
   * value list), which also rejects two series carrying the same values under different modes.
   */
  private static void mergeFilter(
      Filters.Filter target, Filters.Filter incoming, Set<String> divergingKeys) {
    if (effectiveOperator(target) != effectiveOperator(incoming)) {
      throw new IllegalArgumentException(
          "Cannot drill down into series using different operators on '%s': %s vs %s"
              .formatted(
                  incoming.getKey(), effectiveOperator(target), effectiveOperator(incoming)));
    }
    if (valueSet(target).equals(valueSet(incoming))
        && effectiveMode(target) == effectiveMode(incoming)) {
      return;
    }
    divergingKeys.add(incoming.getKey());
    if (!UNION_SAFE_OPERATORS.contains(effectiveOperator(incoming))) {
      throw new IllegalArgumentException(
          "Cannot drill down into series diverging on '%s': a value union under operator %s does"
              + " not express the OR of the series"
                  .formatted(incoming.getKey(), effectiveOperator(incoming)));
    }
    if (effectiveMode(target) == Filters.FilterMode.and
        || effectiveMode(incoming) == Filters.FilterMode.and) {
      throw new IllegalArgumentException(
          "Cannot drill down into series diverging on '%s': a series requires all its values at"
              + " once (and-mode) on that key".formatted(incoming.getKey()));
    }
    target.setValues(unionValues(target.getValues(), incoming.getValues()));
    target.setMode(Filters.FilterMode.or);
  }

  /**
   * Operators whose values the query engine combines disjunctively under or-mode, making a per-key
   * value union equivalent to the OR of the series. Negated operators (not_eq, not_contains)
   * combine their values as must-not clauses regardless of mode, and empty / not_empty ignore
   * values entirely.
   */
  private static final Set<Filters.FilterOperator> UNION_SAFE_OPERATORS =
      Set.of(
          Filters.FilterOperator.eq,
          Filters.FilterOperator.contains,
          Filters.FilterOperator.gt,
          Filters.FilterOperator.gte,
          Filters.FilterOperator.lt,
          Filters.FilterOperator.lte);

  /** Null collapses onto {@code eq}, matching {@link Filters.FilterOperator#fromValue}. */
  private static Filters.FilterOperator effectiveOperator(Filters.Filter filter) {
    return filter.getOperator() == null ? Filters.FilterOperator.eq : filter.getOperator();
  }

  /**
   * The mode as the query engine reads it: only a literal {@code and} conjoins values, anything
   * else (or, null) is disjunctive. With a single value the distinction has no effect at all, so
   * such filters are treated as disjunctive to keep them mergeable.
   */
  private static Filters.FilterMode effectiveMode(Filters.Filter filter) {
    if (nullSafe(filter.getValues()).size() <= 1) {
      return Filters.FilterMode.or;
    }
    return filter.getMode() == Filters.FilterMode.and
        ? Filters.FilterMode.and
        : Filters.FilterMode.or;
  }

  private static Filters.FilterGroup copyFilterGroup(Filters.FilterGroup source) {
    Filters.FilterGroup copy = Filters.FilterGroup.defaultFilterGroup();
    if (source == null) {
      return copy;
    }
    copy.setMode(source.getMode());
    copy.setFilters(
        nullSafeFilters(source.getFilters()).stream()
            .map(
                filter ->
                    new Filters.Filter(
                        filter.getId(),
                        filter.getKey(),
                        filter.getMode(),
                        new ArrayList<>(nullSafe(filter.getValues())),
                        filter.getOperator()))
            .collect(Collectors.toCollection(ArrayList::new)));
    return copy;
  }

  private static Set<String> filterKeys(Filters.FilterGroup group) {
    return nullSafeFilters(group == null ? null : group.getFilters()).stream()
        .map(Filters.Filter::getKey)
        .collect(Collectors.toSet());
  }

  private static Set<String> valueSet(Filters.Filter filter) {
    return new HashSet<>(nullSafe(filter.getValues()));
  }

  private static List<String> unionValues(List<String> left, List<String> right) {
    LinkedHashSet<String> union = new LinkedHashSet<>(nullSafe(left));
    union.addAll(nullSafe(right));
    return new ArrayList<>(union);
  }

  private static List<String> nullSafe(List<String> values) {
    return values == null ? List.of() : values;
  }

  private static List<Filters.Filter> nullSafeFilters(List<Filters.Filter> filters) {
    return filters == null ? List.of() : filters;
  }

  /**
   * Converts a security coverage widget configuration to a list configuration
   *
   * @param widget the source widget containing the configuration to convert
   * @param attackPatternFilterValues attackPatternIds list of attack pattern IDs to filter by
   * @return a ListConfiguration object configured based on the widget settings
   */
  public ListConfiguration convertSecurityCoverageWidgetToListConfiguration(
      Widget widget, Map<String, List<String>> attackPatternFilterValues) {
    // The matrix drills EVERY series the widget declares: both success and failure for
    // stored two-series widgets (#7079), the single scope series for the synthetic
    // overview drill-downs (#7137). Enumerating the declared series beats hardcoding
    // their indexes here: the union is then whatever the widget declares, and cannot
    // fall out of step with it.
    List<WidgetConfigurationWithSeries.Series> series =
        widget.getWidgetConfiguration() instanceof WidgetConfigurationWithSeries config
                && config.getSeries() != null
            ? config.getSeries()
            : List.of();
    List<Integer> seriesIndexes = IntStream.range(0, series.size()).boxed().toList();
    return this.convertWidgetToListConfiguration(widget, seriesIndexes, attackPatternFilterValues);
  }

  /**
   * Manage telemetry event for widgets management
   *
   * @param widget to apply telemetry
   * @param isDeletedEvent to manage event
   */
  private void sendTelemetryEvent(Widget widget, boolean isDeletedEvent) {
    if (WidgetType.AVERAGE.equals(widget.getType())) {
      if (isDeletedEvent) {
        actionMetricCollector.removeAverageCreatedCount();
      } else {
        actionMetricCollector.addAverageCreatedCount();
      }
    }
  }
}
