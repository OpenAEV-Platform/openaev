package io.openaev.injector_contract.fields;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS;

import io.openaev.injector_contract.ContractCardinality;

/**
 * Contract element representing an asset group selector field.
 *
 * <p>Asset group fields allow users to select predefined groups of assets to be targeted by the
 * injection. Asset groups enable targeting multiple related assets simultaneously.
 *
 * @see ContractCardinalityElement
 * @see ContractAsset
 */
public class ContractAssetGroup extends ContractCardinalityElement {

  /**
   * Creates a new asset group field with the specified cardinality.
   *
   * @param cardinality whether to allow single or multiple group selection
   */
  public ContractAssetGroup(ContractCardinality cardinality) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS, "Source asset groups", cardinality);
  }

  /**
   * Creates an asset group selector field.
   *
   * @param cardinality whether to allow single or multiple selection
   * @return a configured ContractAssetGroup instance
   */
  public static ContractAssetGroup assetGroupField(ContractCardinality cardinality) {
    return new ContractAssetGroup(cardinality);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.AssetGroup;
  }
}
