package io.openaev.rest.kill_chain_phase.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KillChainPhaseOutput {

  @JsonProperty("phase_id")
  private String id;

  @JsonProperty("phase_external_id")
  private String externalId;

  @JsonProperty("phase_stix_id")
  private String stixId;

  @JsonProperty("phase_name")
  private String name;

  @JsonProperty("phase_shortname")
  private String shortName;

  @JsonProperty("phase_kill_chain_name")
  private String killChainName;

  @JsonProperty("phase_description")
  private String description;

  @JsonProperty("phase_order")
  private Long order = 0L;

  @JsonProperty("phase_created_at")
  private String createdAt;

  @JsonProperty("phase_updated_at")
  private String updatedAt;
}
