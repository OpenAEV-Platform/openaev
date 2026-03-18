package io.openaev.database.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContractOutputField {
  private String key;
  private ContractOutputTechnicalType type;
  private boolean required;

  public ContractOutputField(String key, ContractOutputTechnicalType type, boolean required) {
    this.key = key;
    this.type = type;
    this.required = required;
  }
}
