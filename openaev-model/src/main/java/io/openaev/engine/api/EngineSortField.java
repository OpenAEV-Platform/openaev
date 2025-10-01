package io.openaev.engine.api;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EngineSortField {
  @NotNull private String fieldName;

  @NotNull private SortDirection direction = SortDirection.ASC;
}
