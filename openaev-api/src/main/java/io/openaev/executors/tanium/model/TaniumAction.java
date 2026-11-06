package io.openaev.executors.tanium.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.openaev.executors.model.ExecutorDispatch;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaniumAction {

  private String agentExternalReference;
  private Integer scriptId;
  private String commandEncoded;
  private ExecutorDispatch dispatch;
}
