package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ContractOutputTechnicalType {
  @JsonProperty("text")
  Text("text"),
  @JsonProperty("number")
  Number("number"),
  @JsonProperty("boolean")
  Boolean("boolean"),
  @JsonProperty("object")
  Object("object");

  public final String label;

  ContractOutputTechnicalType(String label) {
    this.label = label;
  }
}
