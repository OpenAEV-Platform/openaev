package io.openaev.injector_contract.fields;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ContractText extends ContractElement {

  private String defaultValue = "";

  public ContractText(String key, String label) {
    super(key, label);
  }

  public static ContractText textField(String key, String label) {
    return new ContractText(key, label);
  }

  public static ContractText textField(String key, String label, String defaultValue) {
    ContractText contractText = new ContractText(key, label);
    contractText.setDefaultValue(defaultValue);
    return contractText;
  }

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
