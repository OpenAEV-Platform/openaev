package io.openaev.executors.sentinelone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.openaev.database.model.Agent;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SentinelOneAction {

  private List<Agent> agents;
  private String scriptId;
  private String commandEncoded;
}
