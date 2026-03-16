package io.openaev.executors.paloaltocortex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaloAltoCortexAction {

  private String agentExternalReference;
  private String scriptId;
  // The parameters to send a payload to SentinelOne API are different if we are on Unix or Windows
  private PaloAltoCortexCommand commandUnix;
  private PaloAltoCortexCommandList commandWindows;
}
