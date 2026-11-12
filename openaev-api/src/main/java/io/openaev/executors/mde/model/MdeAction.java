package io.openaev.executors.mde.model;

import io.openaev.database.model.Agent;
import lombok.Data;

@Data
public class MdeAction {

  private Agent agent;
  private String scriptName;
  private String commandEncoded;
}
