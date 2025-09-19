package io.openbas.rest.dashboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Getter;

@Getter
public class WidgetToEntitiesInput {

  @JsonProperty("filter_value")
  @Schema(description = "The value to filter the entities by")
  private String filterValue;

  @JsonProperty("series_index")
  @Schema(description = "The index of the series to filter by, if applicable, otherwise 0")
  private Integer seriesIndex;

  @JsonProperty("parameters")
  @Schema(description = "Additional parameters for the widget")
  private Map<String, String> parameters;
}
