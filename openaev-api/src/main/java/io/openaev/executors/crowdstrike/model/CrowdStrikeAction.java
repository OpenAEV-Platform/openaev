package io.openaev.executors.crowdstrike.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.openaev.executors.model.ExecutorDispatch;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrowdStrikeAction {

  private String agentExternalReference;
  private String scriptName;
  private String commandEncoded;
  private ExecutorDispatch dispatch;
}
