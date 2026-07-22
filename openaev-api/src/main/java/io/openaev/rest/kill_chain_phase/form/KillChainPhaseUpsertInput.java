package io.openaev.rest.kill_chain_phase.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

public class KillChainPhaseUpsertInput {

  // @Valid cascades validation to each item: without it, blank kill chain / short names would
  // slip through and NPE in the upsert in-batch key building instead of returning a 400.
  @Valid
  @JsonProperty("kill_chain_phases")
  private List<KillChainPhaseCreateInput> killChainPhases = new ArrayList<>();

  public List<KillChainPhaseCreateInput> getKillChainPhases() {
    return killChainPhases;
  }

  public void setKillChainPhases(List<KillChainPhaseCreateInput> killChainPhases) {
    this.killChainPhases = killChainPhases;
  }
}
