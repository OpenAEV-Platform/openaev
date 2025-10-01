package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
public class ContractDependencySelect extends ContractCardinalityElement {

  private final String dependencyField;
  @Setter private Map<String, Map<String, String>> choices = new HashMap<>();

  public ContractDependencySelect(
      String key, String label, String dependencyField, ContractCardinality cardinality) {
    super(key, label, cardinality);
    this.dependencyField = dependencyField;
  }

  public static ContractDependencySelect dependencySelectField(
      String key, String label, String dependencyField, Map<String, Map<String, String>> choices) {
    ContractDependencySelect contractSelect =
        new ContractDependencySelect(key, label, dependencyField, ContractCardinality.One);
    contractSelect.setChoices(choices);
    return contractSelect;
  }

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
