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
          "Key-value pairs for filtering entities, where the key is the field name and the value is"
              + " the filter criterion")
  private Map<String, List<String>> filterValuesMap;

  @JsonProperty("series_index")
  @Schema(description = "The index of the series to filter by, if applicable, otherwise 0")
  private Integer seriesIndex;

  @JsonProperty("series_indexes")
  @Schema(
      description =
          "The indexes of every series that produced the clicked number, ORed together. Takes"
              + " precedence over series_index; use it whenever a widget displays a total spanning"
              + " several series, so the drilled list resolves to exactly the documents that were"
              + " counted")
  private List<Integer> seriesIndexes;

  @JsonProperty("parameters")
  @Schema(description = "Additional parameters for the widget")
  private Map<String, String> parameters;

  @JsonProperty("pagination")
  @Schema(description = "Pagination for the widget")
  private Pagination pagination;

  /**
   * The series the drill-down applies to, as a list. Callers may send either the single {@code
   * series_index} or the multi-valued {@code series_indexes}; this collapses both onto one shape so
   * no call site has to re-implement the fallback.
   *
   * @return the explicit series indexes, else the single index, else series 0
   */
  public List<Integer> resolvedSeriesIndexes() {
    if (this.seriesIndexes != null && !this.seriesIndexes.isEmpty()) {
      return this.seriesIndexes;
    }
    return List.of(this.seriesIndex == null ? 0 : this.seriesIndex);
  }
}
