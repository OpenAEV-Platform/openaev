package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class InjectExpectationUpdateFromCollectorInput {
  @NotNull
  @JsonProperty("collector_id")
  private String collectorId;

  @Schema(
      deprecated = true,
      description =
          "Ignored for collectors: the result label is derived from is_success and the expectation"
              + " type")
  @JsonProperty("result")
  private String result;

  @NotNull
  @JsonProperty("is_success")
  private Boolean isSuccess;

  @JsonProperty("metadata")
  private Map<String, String> metadata;
}
