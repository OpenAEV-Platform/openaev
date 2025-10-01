package io.openaev.engine.api;

import static io.openaev.config.EngineConfig.Defaults.ENTITIES_CAP;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StructuralHistogramWidget extends HistogramWidget {

  public static final String STRUCTURAL_MODE = "structural";

  @Positive
  @Min(1)
  private int limit = ENTITIES_CAP;

  @NotBlank private String field;

  public StructuralHistogramWidget() {
    super(STRUCTURAL_MODE);
  }
}
