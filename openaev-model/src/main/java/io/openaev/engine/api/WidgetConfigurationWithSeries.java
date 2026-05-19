package io.openaev.engine.api;

import io.openaev.database.model.Filters;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WidgetConfigurationWithSeries extends WidgetConfiguration {

  @NotNull List<Series> series = new ArrayList<>();

  @Data
  public static class Series {
    private String name;
    private Filters.FilterGroup filter = new Filters.FilterGroup();
  }

  public WidgetConfigurationWithSeries(WidgetConfigurationType configurationType) {
    super(configurationType);
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
