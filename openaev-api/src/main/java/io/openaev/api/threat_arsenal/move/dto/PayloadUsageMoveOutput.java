package io.openaev.api.threat_arsenal.move.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.payload.PayloadUsageMoveService.Refusal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** The usages moved to the target version, and the others with why they stayed. */
public record PayloadUsageMoveOutput(
    @JsonProperty("moved_ids") @NotNull List<String> movedIds,
    @JsonProperty("refused") @NotNull List<PayloadUsageRefusal> refused) {

  public record PayloadUsageRefusal(
      @JsonProperty("usage_id") @NotBlank String usageId,
      @JsonProperty("reason") @NotNull Refusal reason) {}
}
