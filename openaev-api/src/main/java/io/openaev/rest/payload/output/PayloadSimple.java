package io.openaev.rest.payload.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Domain;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

  @JsonProperty("payload_domains")
  private String[] domains;

}
