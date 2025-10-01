package io.openaev.engine.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SortDirection {
  @JsonProperty("ASC")
  ASC,
  @JsonProperty("DESC")
  DESC
}
