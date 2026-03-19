package io.openaev.executors.sentinelone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SentinelOneScriptExecute {

  private int affected;
  private String parentTaskId;
}
