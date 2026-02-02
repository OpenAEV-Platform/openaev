package io.openaev.injector_contract.fields;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Contract element representing a multi-line text area field.
 *
 * <p>Text area fields support longer text content and optionally rich text formatting (HTML).
 *
 * @see ContractElement
 * @see ContractText
 */
@Getter
public class ContractTextArea extends ContractElement {

  /** Default value to pre-populate the field. */
  @Setter private String defaultValue = "";

  /** Whether this field supports rich text (HTML) formatting. */
  private final boolean richText;

  /**
   * Creates a new text area field.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param richText whether to enable rich text formatting
   */
  public ContractTextArea(String key, String label, boolean richText) {
    super(key, label);
    this.richText = richText;
  }

  /**
   * Creates a plain text area field.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @return a configured ContractTextArea instance
   */
  public static ContractTextArea textareaField(String key, String label) {
    return new ContractTextArea(key, label, false);
  }

  /**
   * Creates a rich text area field with HTML support.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @return a configured ContractTextArea instance
   */
  public static ContractTextArea richTextareaField(String key, String label) {
    return new ContractTextArea(key, label, true);
  }

  /**
   * Creates a rich text area field with a default value.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param defaultValue the initial value
   * @return a configured ContractTextArea instance
   */
  public static ContractTextArea richTextareaField(String key, String label, String defaultValue) {
    ContractTextArea contractText = new ContractTextArea(key, label, true);
    contractText.setDefaultValue(defaultValue);
    return contractText;
  }

  /**
   * Creates a rich text area field with conditional visibility.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param defaultValue the initial value
   * @param visibleConditionFields fields that control visibility
   * @param values field values that make this field visible
   * @return a configured ContractTextArea instance
   */
  public static ContractTextArea richTextareaField(
      String key,
      String label,
      String defaultValue,
      List<ContractElement> visibleConditionFields,
      Map<String, String> values) {
    ContractTextArea contractText = new ContractTextArea(key, label, true);
    contractText.setDefaultValue(defaultValue);
    contractText.setVisibleConditionFields(
        visibleConditionFields.stream().map(ContractElement::getKey).toList());
    contractText.setVisibleConditionValues(values);
    return contractText;
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Textarea;
  }
}
