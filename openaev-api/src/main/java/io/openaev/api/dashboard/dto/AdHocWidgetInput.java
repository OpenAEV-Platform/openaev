package io.openaev.api.dashboard.dto;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.engine.api.WidgetConfiguration;
import io.openaev.utils.pagination.Pagination;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Input for ad-hoc (non-persisted) widget queries: carries a full widget configuration instead of
 * referencing a stored widget. Used by hardcoded platform dashboards.
 */
@Getter
@Setter
public class AdHocWidgetInput {

  @JsonProperty("widget_config")
  @NotNull(message = MANDATORY_MESSAGE)
  private WidgetConfiguration widgetConfiguration;

  @JsonProperty("parameters")
  private Map<String, String> parameters;

  @JsonProperty("pagination")
  private Pagination pagination;
}
