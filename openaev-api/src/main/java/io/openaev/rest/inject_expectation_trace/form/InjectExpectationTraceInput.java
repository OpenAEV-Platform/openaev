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
  @Schema(implementation = String.class)
  @NotBlank
  private String injectExpectationId;

  @NotBlank
  @JsonProperty("inject_expectation_trace_source_id")
  @Schema(implementation = String.class)
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

  /**
   * How a Logging trace matched the attack ({@code marker}, {@code source_ip} or {@code target_ip},
   * case-insensitive). Read as a string so that an unknown value is dropped instead of rejecting
   * the whole bulk.
   */
  @JsonProperty("inject_expectation_trace_match_type")
  @Schema(allowableValues = {"marker", "source_ip", "target_ip"})
  private String matchType;
}
