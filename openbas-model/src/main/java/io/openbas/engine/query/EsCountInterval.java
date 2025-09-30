package io.openbas.engine.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EsCountInterval {
  @JsonProperty("interval_count")
  @NotBlank
  private Long intervalCount;

  @JsonProperty("previous_interval_count")
  @NotBlank
  private Long previousIntervalCount;

  @JsonProperty("difference_count")
  @NotBlank
  private Long differenceCount;
}
