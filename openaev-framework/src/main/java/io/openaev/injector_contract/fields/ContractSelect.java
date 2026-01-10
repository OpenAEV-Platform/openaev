package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Contract element representing a dropdown selection field.
 *
 * <p>Select fields display a list of predefined choices for the user to select from. The choices
 * map contains value-to-label pairs, where the value is stored and the label is displayed.
 *
 * @see ContractCardinalityElement
 */
@Setter
@Getter
public class ContractSelect extends ContractCardinalityElement {

  /** Map of choice values to display labels. */
  private Map<String, String> choices = new HashMap<>();

  /** Optional additional information for each choice. */
  private Map<String, String> choiceInformation = new HashMap<>();

  /**
   * Creates a new select field.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param cardinality single or multiple selection
   */
  public ContractSelect(String key, String label, ContractCardinality cardinality) {
    super(key, label, cardinality);
  }

  /**
   * Creates a single-select field with predefined choices and default value.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param choices map of values to labels
   * @param def the default selected value
   * @return a configured ContractSelect instance
   */
  public static ContractSelect selectFieldWithDefault(
      String key, String label, Map<String, String> choices, String def) {
    ContractSelect contractSelect = new ContractSelect(key, label, ContractCardinality.One);
    contractSelect.setChoices(choices);
    contractSelect.setDefaultValue(List.of(def));
    return contractSelect;
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Select;
  }
}
