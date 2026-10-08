package io.openaev.engine.api;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EngineSortField implements Serializable {
  @NotNull private String fieldName;

  @NotNull private SortDirection direction = SortDirection.ASC;
}
