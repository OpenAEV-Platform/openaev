package io.openaev.executors.paloaltocortex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BodyScriptRun {

  private List<PaloAltoCortexFilter> filters;
  private String script_uid;
  private Object parameters_values;
}
