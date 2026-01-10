package io.openaev.injector_contract.fields;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSETS;

import io.openaev.injector_contract.ContractCardinality;

/**
 * Contract element representing an asset selector field.
 *
 * <p>Asset fields allow users to select source assets (endpoints, servers, etc.) to be targeted by
 * the injection. Selected assets determine where the injection will be executed.
 *
 * @see ContractCardinalityElement
 * @see ContractAssetGroup
 */
public class ContractAsset extends ContractCardinalityElement {

  /**
   * Creates a new asset field with the specified cardinality.
   *
   * @param cardinality whether to allow single or multiple asset selection
   */
  public ContractAsset(ContractCardinality cardinality) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_ASSETS, "Source assets", cardinality);
  }

  /**
   * Creates an asset selector field.
   *
   * @param cardinality whether to allow single or multiple selection
   * @return a configured ContractAsset instance
   */
  public static ContractAsset assetField(ContractCardinality cardinality) {
    return new ContractAsset(cardinality);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Asset;
  }
}
