package io.openaev.executors.tanium.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageInfo {

  private String endCursor;
  private boolean hasNextPage;
}
