package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class InjectExpectationUpdateInput {
  @NotNull
  @JsonProperty("collector_id")
  private String collectorId;

  @NotNull
  @JsonProperty("result")
  private String result;

  @NotNull
  @JsonProperty("is_success")
  private Boolean isSuccess;

  @JsonProperty("metadata")
  private Map<String, String> metadata;
}
