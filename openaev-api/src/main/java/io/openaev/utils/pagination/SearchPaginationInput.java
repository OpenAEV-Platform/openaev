package io.openaev.utils.pagination;

import io.openaev.database.model.Filters.FilterGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPaginationInput extends Pagination {

  @Schema(description = "Filter object to search within filterable attributes")
  private FilterGroup filterGroup;

  @Schema(description = "Text to search within searchable attributes")
  private String textSearch;

  @Schema(
      description =
          "List of sort fields : a field is composed of a property (for instance \"label\" and an optional direction (\"asc\" is assumed if no direction is specified) : (\"desc\", \"asc\")")
  private List<SortField> sorts = new ArrayList<>();
}
