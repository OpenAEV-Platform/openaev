package io.openaev.executors.sentinelone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SentinelOneNetwork {

  private List<String> inet; // ip
  private String physical; // mac
}
