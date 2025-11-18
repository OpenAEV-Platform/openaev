package io.openaev.executors.sentinelone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SentinelOneData {

  private String inputParams;
  private String scriptId;
  private String outputDestination = "SentinelCloud";
  private String taskDescription = "OpenAEV Script";
}
