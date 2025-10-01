package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;

public class ContractTargetedAsset extends ContractCardinalityElement {

  public ContractTargetedAsset(String key, String label) {
    super(key, label, ContractCardinality.Multiple);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.TargetedAsset;
  }
}
