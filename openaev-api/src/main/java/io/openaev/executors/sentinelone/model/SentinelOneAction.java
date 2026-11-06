package io.openaev.executors.sentinelone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.openaev.executors.model.ExecutorDispatch;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SentinelOneAction {

  private String agentExternalReference;
  private String scriptId;
  private String commandEncoded;
  private ExecutorDispatch dispatch;
}
