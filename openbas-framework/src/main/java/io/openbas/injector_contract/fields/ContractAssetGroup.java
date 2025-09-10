package io.openbas.injector_contract.fields;

import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS;

import io.openbas.injector_contract.ContractCardinality;

public class ContractAssetGroup extends ContractCardinalityElement {

  public ContractAssetGroup(ContractCardinality cardinality) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS, "Source asset groups", cardinality);
  }

  public static ContractAssetGroup assetGroupField(ContractCardinality cardinality) {
    return new ContractAssetGroup(cardinality);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.AssetGroup;
  }
}
