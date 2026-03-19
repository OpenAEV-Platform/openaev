package io.openaev.executors.paloaltocortex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaloAltoCortexEndpointReply {

  // Reply OK
  private int total_count;
  private int result_count;
  private List<PaloAltoCortexEndpoint> endpoints;

  // Reply KO
  private Integer err_code;
  private String err_msg;
  private String err_extra;
}
