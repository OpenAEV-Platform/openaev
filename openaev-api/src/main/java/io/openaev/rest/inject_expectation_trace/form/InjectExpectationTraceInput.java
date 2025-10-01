package io.openaev.rest.inject_expectation_trace.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;

@Data
public class InjectExpectationTraceInput {

  @JsonProperty("inject_expectation_trace_expectation")
  @Schema(type = "string")
  @NotBlank
  private String injectExpectationId;

  @NotBlank
  @JsonProperty("inject_expectation_trace_source_id")
  @Schema(type = "string")
  private String sourceId;

  @NotBlank
  @JsonProperty("inject_expectation_trace_alert_name")
  private String alertName;

  @NotBlank
  @JsonProperty("inject_expectation_trace_alert_link")
  private String alertLink;

  @NotNull
  @JsonProperty("inject_expectation_trace_date")
  private Instant alertDate;
}
