package io.openaev.engine.model.vulnerableendpoint;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum VulnerableEndpointAction {
  @JsonProperty("OK")
  OK,
  @JsonProperty("Update")
  UPDATE,
  @JsonProperty("Replace")
  REPLACE,
}
