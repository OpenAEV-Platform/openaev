package io.openaev.executors.crowdstrike.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrowdStrikeAction {

  private String agentExternalReference;
  private String agentId;
  private String injectId;
  private String scriptName;
  private String commandEncoded;
}
