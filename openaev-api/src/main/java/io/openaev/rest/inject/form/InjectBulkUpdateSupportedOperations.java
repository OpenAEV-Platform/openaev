package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/** Represent the supported operations that can be performed in a bulk update of injects */
@Getter
public enum InjectBulkUpdateSupportedOperations {
  @JsonProperty("add")
  ADD("ADD"),
  @JsonProperty("remove")
  REMOVE("REMOVE"),
  @JsonProperty("replace")
  REPLACE("REPLACE");

  private final String value;

  InjectBulkUpdateSupportedOperations(final String value) {
    this.value = value;
  }
}
