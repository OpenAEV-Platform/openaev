package io.openaev.injector_contract.fields;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Contract element representing a boolean checkbox field.
 *
 * <p>Checkbox fields allow users to toggle a boolean value on or off. They are useful for optional
 * features, confirmations, or binary choices.
 *
 * @see ContractElement
 */
@Setter
@Getter
public class ContractCheckbox extends ContractElement {

  /** Default checked state for the checkbox. */
  private boolean defaultValue = false;

  /**
   * Creates a new checkbox field.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   */
  public ContractCheckbox(String key, String label) {
    super(key, label);
  }

  /**
   * Creates a checkbox field with a default checked state.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param checked the initial checked state
   * @return a configured ContractCheckbox instance
   */
  public static ContractCheckbox checkboxField(String key, String label, boolean checked) {
    ContractCheckbox contractCheckbox = new ContractCheckbox(key, label);
    contractCheckbox.setDefaultValue(checked);
    return contractCheckbox;
  }

  /**
   * Creates a checkbox field with conditional visibility.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param checked the initial checked state
   * @param visibleConditionFields fields that control visibility
   * @param visibleConditionValues field values that make this field visible
   * @return a configured ContractCheckbox instance
   */
  public static ContractCheckbox checkboxField(
      String key,
      String label,
      boolean checked,
      List<ContractElement> visibleConditionFields,
      Map<String, String> visibleConditionValues) {
    ContractCheckbox contractCheckbox = new ContractCheckbox(key, label);
    contractCheckbox.setDefaultValue(checked);
    contractCheckbox.setVisibleConditionFields(
        visibleConditionFields.stream().map(ContractElement::getKey).toList());
    contractCheckbox.setVisibleConditionValues(visibleConditionValues);
    return contractCheckbox;
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Checkbox;
  }
}
