package io.openaev.model;

import io.openaev.injector_contract.fields.ContractElement;
import io.openaev.injector_contract.fields.ContractFieldType;

public class LinkedFieldModel {

  private String key;

  private ContractFieldType type;

  private LinkedFieldModel(String key, ContractFieldType type) {
    this.key = key;
    this.type = type;
  }

  public static LinkedFieldModel fromField(ContractElement fieldContract) {
    return new LinkedFieldModel(fieldContract.getKey(), fieldContract.getType());
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public ContractFieldType getType() {
    return type;
  }

  public void setType(ContractFieldType type) {
    this.type = type;
  }
}
