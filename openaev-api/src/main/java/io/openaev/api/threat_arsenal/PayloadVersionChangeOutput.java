package io.openaev.api.threat_arsenal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record PayloadVersionChangeOutput(
    @JsonProperty("moved_ids") List<String> movedIds,
    @JsonProperty("refused") List<Refusal> refused) {

  public record Refusal(
      @JsonProperty("usage_id") @NotBlank String id,
      @JsonProperty("reason") @NotBlank String reason) {}
}
