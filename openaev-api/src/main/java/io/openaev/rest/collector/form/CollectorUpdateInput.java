package io.openaev.rest.collector.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CollectorUpdateInput {

  @JsonProperty("collector_last_execution")
  private Instant lastExecution;
}
