package io.openbas.injector_contract.fields;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ContractCheckbox extends ContractElement {

  private boolean defaultValue = false;

  public ContractCheckbox(String key, String label) {
    super(key, label);
  }

  public static ContractCheckbox checkboxField(String key, String label, boolean checked) {
    ContractCheckbox contractCheckbox = new ContractCheckbox(key, label);
    contractCheckbox.setDefaultValue(checked);
    return contractCheckbox;
  }

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
