package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InjectReceptionInput {

  @JsonProperty("tracking_total_count")
  private int trackingTotalCount;
}
