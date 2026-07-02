package io.openaev.executors.mde.model;

import io.openaev.database.model.Agent;
import java.util.List;
import lombok.Data;

@Data
public class MdeAction {

  private List<Agent> agents;
  private String scriptName;
  private String commandEncoded;
}
