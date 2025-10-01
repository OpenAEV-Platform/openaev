package io.openaev.rest.inject_expectation_trace.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.config.AppConfig;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class InjectExpectationTraceBulkInsertInput {

  @JsonProperty("expectation_traces")
  @NotNull(message = AppConfig.MANDATORY_MESSAGE)
  private List<InjectExpectationTraceInput> expectationTraces;
}
