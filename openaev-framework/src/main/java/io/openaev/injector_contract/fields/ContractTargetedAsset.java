package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;

/**
 * Contract element representing a targeted asset selector with property selection.
 *
 * <p>Targeted asset fields allow users to select assets and specify which property (hostname, IP,
 * etc.) should be used for targeting. This enables flexible targeting based on asset attributes.
 *
 * @see ContractCardinalityElement
 * @see io.openaev.injector_contract.ContractTargetedProperty
 */
public class ContractTargetedAsset extends ContractCardinalityElement {

  /**
   * Creates a new targeted asset field.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   */
  public ContractTargetedAsset(String key, String label) {
    super(key, label, ContractCardinality.Multiple);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.TargetedAsset;
  }
}
