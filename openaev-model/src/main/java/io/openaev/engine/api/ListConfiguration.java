package io.openaev.engine.api;

import static io.openaev.config.EngineConfig.Defaults.ENTITIES_CAP;

import io.openaev.database.model.Filters;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListConfiguration extends WidgetConfiguration {

  @NotNull ListPerspective perspective;

  List<String> columns = new ArrayList<>();

  List<EngineSortField> sorts;

  @Positive
  @Min(1)
  int limit = ENTITIES_CAP;

  @Data
  public static class ListPerspective {
    private String name;
    private Filters.FilterGroup filter = new Filters.FilterGroup();
  }

  public ListConfiguration() {
    super(WidgetConfigurationType.LIST);
  }

  @Override
  public void remap(Map<String, String> map) {
    if (this.perspective != null) {
      if (perspective.getFilter() != null
          && perspective.getFilter().getFilters() != null
          && !perspective.getFilter().getFilters().isEmpty()) {
        for (Filters.Filter filter : perspective.getFilter().getFilters()) {
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
