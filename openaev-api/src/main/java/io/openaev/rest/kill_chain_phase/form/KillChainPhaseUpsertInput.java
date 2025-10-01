package io.openaev.rest.kill_chain_phase.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class KillChainPhaseUpsertInput {

  @JsonProperty("kill_chain_phases")
  private List<KillChainPhaseCreateInput> killChainPhases = new ArrayList<>();

  public List<KillChainPhaseCreateInput> getKillChainPhases() {
    return killChainPhases;
  }

  public void setKillChainPhases(List<KillChainPhaseCreateInput> killChainPhases) {
    this.killChainPhases = killChainPhases;
  }
}
