package io.openaev.utils.es;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.Pagination;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WidgetToEntitiesInput {

  @JsonProperty("filter_values_map")
  @Schema(
      description =
          "Key-value pairs for filtering entities, where the key is the field name and the value is the filter criterion")
  private Map<String, List<String>> filterValuesMap;

  @JsonProperty("series_index")
  @Schema(description = "The index of the series to filter by, if applicable, otherwise 0")
  private Integer seriesIndex;

  @JsonProperty("parameters")
  @Schema(description = "Additional parameters for the widget")
  private Map<String, String> parameters;

  @JsonProperty("pagination")
  @Schema(description = "Pagination for the widget")
  private Pagination pagination;
}
