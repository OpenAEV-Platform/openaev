package io.openaev.api.catalog.dto.action;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A catalogue payload to install, at its latest offered version. */
public record AvailablePayloadActionOutput(
    @JsonProperty("action_id") @NotBlank String id,
    @JsonProperty("action_labels") @NotNull Map<String, String> labels,
    @JsonProperty("action_domain") String domain,
    @JsonProperty("action_platforms") @NotNull List<String> platforms,
    @JsonProperty("action_payload_version") @NotBlank String version,
    @JsonProperty("action_payload_description") String description,
    @JsonProperty("action_payload_published_at") Instant publishedAt)
    implements AvailableActionOutput {

  @JsonProperty(KIND)
  @Schema(hidden = true)
  public String kind() {
    return PAYLOAD;
  }
}
