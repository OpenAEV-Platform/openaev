package io.openaev.api.threat_arsenal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record PayloadVersionChangeInput(
    @JsonProperty("target_payload_id") @NotBlank String targetPayloadId,
    @JsonProperty("inject_ids") List<String> injectIds,
    @JsonProperty("step_ids") List<String> stepIds) {

  public List<String> injectIdsOrEmpty() {
    return injectIds == null ? List.of() : injectIds;
  }

  public List<String> stepIdsOrEmpty() {
    return stepIds == null ? List.of() : stepIds;
  }
}
