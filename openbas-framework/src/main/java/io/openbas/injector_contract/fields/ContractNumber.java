package io.openbas.injector_contract.fields;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ContractNumber extends ContractElement {

  private String defaultValue = "";

  public ContractNumber(String key, String label) {
    super(key, label);
  }

  public static ContractNumber textField(String key, String label) {
    return new ContractNumber(key, label);
  }

  public static ContractNumber numberField(String key, String label, String defaultValue) {
    ContractNumber contractNumber = new ContractNumber(key, label);
    contractNumber.setDefaultValue(defaultValue);
    return contractNumber;
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Number;
  }
}
