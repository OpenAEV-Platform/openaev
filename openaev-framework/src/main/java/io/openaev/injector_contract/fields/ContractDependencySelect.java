package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Contract element representing a select field whose options depend on another field's value.
 *
 * <p>Dependency select fields provide dynamic options based on the current value of a referenced
 * field. This is useful for cascading selections like country → city or category → subcategory.
 *
 * <p>The choices map is structured as: {@code dependency_value → (choice_value → choice_label)}
 *
 * @see ContractCardinalityElement
 */
@Getter
public class ContractDependencySelect extends ContractCardinalityElement {

  /** The key of the field this select depends on. */
  private final String dependencyField;

  /** Map of dependency values to their corresponding choice maps (value → label). */
  @Setter private Map<String, Map<String, String>> choices = new HashMap<>();

  /**
   * Creates a new dependency select field.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param dependencyField the key of the field this depends on
   * @param cardinality single or multiple selection
   */
  public ContractDependencySelect(
      String key, String label, String dependencyField, ContractCardinality cardinality) {
    super(key, label, cardinality);
    this.dependencyField = dependencyField;
  }

  /**
   * Creates a single-select dependency field.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param dependencyField the key of the field this depends on
   * @param choices map of dependency values to choice maps
   * @return a configured ContractDependencySelect instance
   */
  public static ContractDependencySelect dependencySelectField(
      String key, String label, String dependencyField, Map<String, Map<String, String>> choices) {
    ContractDependencySelect contractSelect =
        new ContractDependencySelect(key, label, dependencyField, ContractCardinality.One);
    contractSelect.setChoices(choices);
    return contractSelect;
  }

  /**
   * Creates a multi-select dependency field.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param dependencyField the key of the field this depends on
   * @param choices map of dependency values to choice maps
   * @return a configured ContractDependencySelect instance
   */
  public static ContractDependencySelect dependencyMultiSelectField(
      String key, String label, String dependencyField, Map<String, Map<String, String>> choices) {
    ContractDependencySelect contractSelect =
        new ContractDependencySelect(key, label, dependencyField, ContractCardinality.Multiple);
    contractSelect.setChoices(choices);
    return contractSelect;
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.DependencySelect;
  }
}
