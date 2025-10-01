package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WidgetLayout {

  @JsonProperty("widget_layout_w")
  @NotNull
  protected int w;

  @JsonProperty("widget_layout_h")
  @NotNull
  protected int h;

  @JsonProperty("widget_layout_x")
  @NotNull
  protected int x;

  @JsonProperty("widget_layout_y")
  @NotNull
  protected int y;
}
