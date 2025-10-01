package io.openaev.stix.types.inner;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KillChainPhase {
  @JsonProperty("kill_chain_name")
  private String killChainName;

  @JsonProperty("phase_name")
  private String phaseName;

  @JsonProperty("x_opencti_order")
  private Integer xOpenCtiOrder;

  public static KillChainPhase parseKillChainPhase(JsonNode node) {
    KillChainPhase phase = new KillChainPhase();
    phase.setKillChainName(node.get("kill_chain_name").asText());
    phase.setPhaseName(node.get("phase_name").asText());
    if (node.has("x_opencti_order")) phase.setXOpenCtiOrder(node.get("x_opencti_order").asInt());
    return phase;
  }
}
