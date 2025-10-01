package io.openaev.rest.payload.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class PayloadSimple {

  @JsonProperty("payload_id")
  private String id;

  @JsonProperty("payload_type")
  private String type;

  @JsonProperty("payload_collector_type")
  private String collectorType;
}
