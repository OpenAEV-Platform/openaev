package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;

/**
 * Contract element referencing existing asset groups as a parameter of the inject.
 *
 * <p>The inject form renders a searchable asset group picker and stores the selected asset group id
 * (cardinality one) or list of ids (cardinality multiple) in the inject content under the field
 * key. Unlike {@link ContractAssetGroup}, the selection does not target the inject: no expectation
 * is computed on the group's assets, and a contract may declare several such fields.
 *
 * @see ContractCardinalityElement
 * @see ContractAssetGroup
 */
public class ContractAssetGroupReference extends ContractCardinalityElement {

  /**
   * Creates a new asset group reference field.
   *
   * @param key the unique identifier for this field (the inject content key)
   * @param label the display label
   * @param cardinality whether to allow single or multiple selection
   */
  public ContractAssetGroupReference(String key, String label, ContractCardinality cardinality) {
    super(key, label, cardinality);
  }

  /**
   * Creates an asset group reference field.
   *
   * @param key the unique identifier for this field (the inject content key)
   * @param label the display label
   * @param cardinality whether to allow single or multiple selection
   * @return a configured ContractAssetGroupReference instance
   */
  public static ContractAssetGroupReference assetGroupReferenceField(
      String key, String label, ContractCardinality cardinality) {
    return new ContractAssetGroupReference(key, label, cardinality);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.AssetGroupReference;
  }
}
