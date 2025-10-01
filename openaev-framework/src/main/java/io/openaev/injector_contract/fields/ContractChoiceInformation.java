package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ContractChoiceInformation extends ContractCardinalityElement {
  private List<ChoiceItem> choices = List.of();

  public ContractChoiceInformation(String key, String label, ContractCardinality cardinality) {
    super(key, label, cardinality);
  }

  @Getter
  public static class ChoiceItem {
    private final String label;
    private final String value;
    private final String information;

    public ChoiceItem(String label, String value, String information) {
      this.information = information;
      this.label = label;
      this.value = value;
    }
  }

  public static ContractChoiceInformation choiceInformationField(
      String key, String label, Map<String, String> choiceInformations, String def) {
    ContractChoiceInformation contractChoice =
        new ContractChoiceInformation(key, label, ContractCardinality.One);

    ArrayList<ChoiceItem> choiceItems = new ArrayList<>();
    for (Map.Entry<String, String> entry : choiceInformations.entrySet()) {
      choiceItems.add(new ChoiceItem(entry.getKey(), entry.getKey(), entry.getValue()));
    }

    contractChoice.setChoices(choiceItems);
    contractChoice.setDefaultValue(List.of(def));
    return contractChoice;
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Choice;
  }
}
