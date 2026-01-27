package io.openaev.engine.api;

import static io.openaev.utils.CustomDashboardTimeRange.DEFAULT;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.openaev.database.model.Filters;
import io.openaev.jsonapi.CanRemapWeakRelationships;
import io.openaev.utils.CustomDashboardTimeRange;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
// @Schema(
//    discriminatorProperty = "widget_configuration_type",
//    oneOf = {HistogramWidget.class, ListConfiguration.class, FlatConfiguration.class},
//    discriminatorMapping = {
//      @DiscriminatorMapping(
//          value = WidgetConfigurationType.Values.FLAT,
//          schema = FlatConfiguration.class),
//      @DiscriminatorMapping(
//          value = WidgetConfigurationType.Values.LIST,
//          schema = ListConfiguration.class),
//      @DiscriminatorMapping(
//          value = WidgetConfigurationType.Values.TEMPORAL_HISTOGRAM,
//          schema = DateHistogramWidget.class),
//      @DiscriminatorMapping(
//          value = WidgetConfigurationType.Values.STRUCTURAL_HISTOGRAM,
//          schema = StructuralHistogramWidget.class),
//    })
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "widget_configuration_type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = FlatConfiguration.class, name = WidgetConfigurationType.Values.FLAT),
  @JsonSubTypes.Type(
      value = AverageConfiguration.class,
      name = WidgetConfigurationType.Values.AVERAGE),
  @JsonSubTypes.Type(value = ListConfiguration.class, name = WidgetConfigurationType.Values.LIST),
  @JsonSubTypes.Type(
      value = DateHistogramWidget.class,
      name = WidgetConfigurationType.Values.TEMPORAL_HISTOGRAM),
  @JsonSubTypes.Type(
      value = StructuralHistogramWidget.class,
      name = WidgetConfigurationType.Values.STRUCTURAL_HISTOGRAM)
})
public abstract class WidgetConfiguration implements CanRemapWeakRelationships {

  @Setter(NONE)
  @NotNull
  @JsonProperty("widget_configuration_type")
  private WidgetConfigurationType configurationType;

  @JsonProperty("title")
  private String title;

  @Nullable private String start; // Date or $custom_dashboard_start

  @Nullable private String end; // Date or $custom_dashboard_end

  @NotNull
  @JsonProperty("time_range")
  private CustomDashboardTimeRange timeRange = DEFAULT;

  @NotBlank
  @JsonProperty("date_attribute")
  private String dateAttribute = "base_created_at";

  @NotNull List<Series> series = new ArrayList<>();

  @Data
  public static class Series {
    private String name;
    private Filters.FilterGroup filter = new Filters.FilterGroup();
  }

  WidgetConfiguration(WidgetConfigurationType configurationType) {
    this.configurationType = configurationType;
  }

  @Override
  public void remap(Map<String, String> map) {
    if (this.series != null && !this.series.isEmpty()) {
      for (Series currentSeries : this.series) {
        if (currentSeries.getFilter() != null
            && currentSeries.getFilter().getFilters() != null
            && !currentSeries.getFilter().getFilters().isEmpty()) {
          for (Filters.Filter filter : currentSeries.getFilter().getFilters()) {
            if (filter.getValues() != null) {
              for (Map.Entry<String, String> switchPair : map.entrySet()) {
                if (filter.getValues().contains(switchPair.getKey())) {
                  filter.getValues().remove(switchPair.getKey());
                  filter.getValues().add(switchPair.getValue());
                }
              }
            }
          }
        }
      }
    }
  }
}
