package io.openaev.rest.executor.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExecutorUpdateInput {

  @JsonProperty("executor_last_execution")
  private Instant lastExecution;
}
