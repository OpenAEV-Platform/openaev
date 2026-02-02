package io.openaev.injector_contract.fields;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Contract element representing a single-line text input field.
 *
 * <p>Text fields are used for short string inputs like names, identifiers, or brief descriptions.
 *
 * @see ContractElement
 * @see ContractTextArea
 */
@Setter
@Getter
public class ContractText extends ContractElement {

  /** Default value to pre-populate the field. */
  private String defaultValue = "";

  /**
   * Creates a new text field.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   */
  public ContractText(String key, String label) {
    super(key, label);
  }

  /**
   * Creates a text field with no default value.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @return a configured ContractText instance
   */
  public static ContractText textField(String key, String label) {
    return new ContractText(key, label);
  }

  /**
   * Creates a text field with a default value.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param defaultValue the initial value
   * @return a configured ContractText instance
   */
  public static ContractText textField(String key, String label, String defaultValue) {
    ContractText contractText = new ContractText(key, label);
    contractText.setDefaultValue(defaultValue);
    return contractText;
  }

  /**
   * Creates a text field with conditional visibility.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param defaultValue the initial value
   * @param visibleConditionFields fields that control visibility
   * @param values field values that make this field visible
   * @return a configured ContractText instance
   */
  public static ContractText textField(
      String key,
      String label,
      String defaultValue,
      List<ContractElement> visibleConditionFields,
      Map<String, String> values) {
    ContractText contractText = new ContractText(key, label);
    contractText.setDefaultValue(defaultValue);
    contractText.setVisibleConditionFields(
        visibleConditionFields.stream().map(ContractElement::getKey).toList());
    contractText.setVisibleConditionValues(values);
    return contractText;
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Text;
  }
}
