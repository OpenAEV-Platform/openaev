package io.openaev.executors.paloaltocortex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaloAltoCortexScriptRun {

  // Reply OK
  private int action_id;
  private int status;
  private int endpoints_count;

  // Reply KO
  private Integer err_code;
  private String err_msg;
  private String err_extra;
}
