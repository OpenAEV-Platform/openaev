package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;

/**
 * Contract element selecting existing tags as a parameter of the inject.
 *
 * <p>The inject form renders the tag picker and stores the list of selected tag ids in the inject
 * content under the field key.
 *
 * @see ContractCardinalityElement
 */
public class ContractTags extends ContractCardinalityElement {

  /**
   * Creates a new tags field (always multiple).
   *
   * @param key the unique identifier for this field (the inject content key)
   * @param label the display label
   */
  public ContractTags(String key, String label) {
    super(key, label, ContractCardinality.Multiple);
  }

  /**
   * Creates a tags field.
   *
   * @param key the unique identifier for this field (the inject content key)
   * @param label the display label
   * @return a configured ContractTags instance
   */
  public static ContractTags tagsField(String key, String label) {
    return new ContractTags(key, label);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Tags;
  }
}
