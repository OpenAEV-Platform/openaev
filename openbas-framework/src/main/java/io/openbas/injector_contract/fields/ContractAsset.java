package io.openbas.injector_contract.fields;

import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSETS;

import io.openbas.injector_contract.ContractCardinality;

public class ContractAsset extends ContractCardinalityElement {

  public ContractAsset(ContractCardinality cardinality) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_ASSETS, "Source assets", cardinality);
  }

  public static ContractAsset assetField(ContractCardinality cardinality) {
    return new ContractAsset(cardinality);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Asset;
  }
}
