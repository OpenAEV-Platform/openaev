package io.openaev.injector_contract.fields;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
public class ContractTextArea extends ContractElement {

  @Setter private String defaultValue = "";
  private final boolean richText;

  public ContractTextArea(String key, String label, boolean richText) {
    super(key, label);
    this.richText = richText;
  }

  public static ContractTextArea textareaField(String key, String label) {
    return new ContractTextArea(key, label, false);
  }

  public static ContractTextArea richTextareaField(String key, String label) {
    return new ContractTextArea(key, label, true);
  }

  public static ContractTextArea richTextareaField(String key, String label, String defaultValue) {
    ContractTextArea contractText = new ContractTextArea(key, label, true);
    contractText.setDefaultValue(defaultValue);
    return contractText;
  }

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
