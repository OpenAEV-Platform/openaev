package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ContractSelect extends ContractCardinalityElement {

  private Map<String, String> choices = new HashMap<>();
  private Map<String, String> choiceInformations = new HashMap<>();

  public ContractSelect(String key, String label, ContractCardinality cardinality) {
    super(key, label, cardinality);
  }

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
