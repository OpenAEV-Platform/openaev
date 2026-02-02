package io.openaev.executors.sentinelone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseAgent {

  private List<SentinelOneAgent> data;
  private List<SentinelOneError> errors;
  private SentinelOnePagination pagination;
}
