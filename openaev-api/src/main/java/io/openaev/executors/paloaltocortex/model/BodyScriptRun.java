package io.openaev.executors.paloaltocortex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BodyScriptRun {

  private PaloAltoCortexFilter filters;
  private String script_uid;
  private PaloAltoCortexCommand parameters_values;
}
