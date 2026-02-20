package io.openaev.engine.api;

import io.openaev.database.model.CustomDashboardParameters;
import io.openaev.utils.pagination.Pagination;
import jakarta.annotation.Nullable;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListRuntime extends Runtime {

  private ListConfiguration widget;
  private Pagination pagination;

  public ListRuntime(
      ListConfiguration widget,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters,
      @Nullable Pagination pagination) {
    this.widget = widget;
    this.parameters = parameters;
    this.definitionParameters = definitionParameters;
    this.pagination = pagination != null ? pagination : new Pagination(0, widget.getLimit());
  }
}
