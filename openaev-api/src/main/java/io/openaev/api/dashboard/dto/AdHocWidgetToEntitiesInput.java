package io.openaev.api.dashboard.dto;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.engine.api.WidgetConfiguration;
import io.openaev.engine.api.WidgetType;
import io.openaev.utils.es.WidgetToEntitiesInput;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Input for converting an ad-hoc (non-persisted) widget into a scoped entity list at runtime. Used
 * by the built-in platform default home dashboard drill-downs: carries the full widget
 * configuration and type instead of referencing a stored widget, plus the clicked filter values,
 * series index and pagination inherited from {@link WidgetToEntitiesInput}.
 */
@Getter
@Setter
public class AdHocWidgetToEntitiesInput extends WidgetToEntitiesInput {

  @JsonProperty("widget_config")
  @NotNull(message = MANDATORY_MESSAGE)
  private WidgetConfiguration widgetConfiguration;

  @JsonProperty("widget_type")
  @NotNull(message = MANDATORY_MESSAGE)
  private WidgetType widgetType;
}
