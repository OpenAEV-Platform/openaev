package io.openaev.rest.kill_chain_phase.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KillChainPhaseOutput {

  @JsonProperty("phase_id")
  @NotBlank
  @Schema(description = "ID of the phase")
  private String id;

  @JsonProperty("phase_external_id")
  @NotBlank
  @Schema(description = "External ID of the phase")
  private String externalId;

  @JsonProperty("phase_stix_id")
  @Schema(description = "Stix ID of the phase")
  private String stixId;

  @JsonProperty("phase_name")
  @NotBlank
  @Schema(description = "Name of the phase")
  private String name;

  @JsonProperty("phase_shortname")
  @NotBlank
  @Schema(description = "Short name of the phase")
  private String shortName;

  @JsonProperty("phase_kill_chain_name")
  @NotBlank
  @Schema(description = "Name of the kill chain phase")
  private String killChainName;

  @JsonProperty("phase_description")
  @Schema(description = "Description of the phase")
  private String description;

  @JsonProperty("phase_order")
  @Schema(description = "Order of the phase")
  private Long order = 0L;

  @JsonProperty("phase_created_at")
  @NotNull
  @Schema(description = "Creation date of the phase")
  private String createdAt;

  @JsonProperty("phase_updated_at")
  @NotNull
  @Schema(description = "Update date of the phase")
  private String updatedAt;
}
