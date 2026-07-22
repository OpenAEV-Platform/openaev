package io.openaev.executors.crowdstrike.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrowdstrikeMeta {

  @JsonProperty("trace_id")
  private String traceId;

  private CrowdstrikePagination pagination;
}
