package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Contract element representing a choice field with additional information for each option.
 *
 * <p>This field type displays a selection with extra context/help text for each choice, helping
 * users understand the implications of their selection.
 */
@Setter
@Getter
public class ContractChoiceInformation extends ContractCardinalityElement {

  private List<ChoiceItem> choices = List.of();

  public ContractChoiceInformation(String key, String label, ContractCardinality cardinality) {
    super(key, label, cardinality);
  }

  /**
   * Represents a single choice item with its label, value, and additional information.
   *
   * <p>The label is displayed to the user, the value is the actual stored data, and the information
   * provides additional context about the choice.
   */
  @Getter
  public static class ChoiceItem {
    private final String label;
    private final String value;
    private final String information;

    /**
     * Creates a new choice item.
     *
     * @param label the display label for the choice
     * @param value the actual value stored when this choice is selected
     * @param information additional context or help text for this choice
     */
    public ChoiceItem(String label, String value, String information) {
      this.label = label;
      this.value = value;
      this.information = information;
    }
  }

  /**
   * Creates a choice information field where the map keys serve as both labels and values.
   *
   * <p>This is a convenience method for cases where the display label and stored value are
   * identical.
   *
   * @param key the unique identifier for this field
   * @param label the display label for the field
   * @param choiceInformations map of choice key (used as both label and value) to information text
   * @param def the default selected value
   * @return a configured ContractChoiceInformation instance
   */
  public static ContractChoiceInformation choiceInformationField(
      String key, String label, Map<String, String> choiceInformations, String def) {
    ContractChoiceInformation contractChoice =
        new ContractChoiceInformation(key, label, ContractCardinality.One);

    List<ChoiceItem> choiceItems = new ArrayList<>();
    for (Map.Entry<String, String> entry : choiceInformations.entrySet()) {
      // Key is used for both label and value (common use case where they're identical)
      choiceItems.add(new ChoiceItem(entry.getKey(), entry.getKey(), entry.getValue()));
    }

    contractChoice.setChoices(choiceItems);
    contractChoice.setDefaultValue(List.of(def));
    return contractChoice;
  }

  /**
   * Creates a choice information field with separate labels, values, and information.
   *
   * @param key the unique identifier for this field
   * @param label the display label for the field
   * @param choices list of pre-configured choice items
   * @param def the default selected value
   * @return a configured ContractChoiceInformation instance
   */
  public static ContractChoiceInformation choiceInformationFieldWithItems(
      String key, String label, List<ChoiceItem> choices, String def) {
    ContractChoiceInformation contractChoice =
        new ContractChoiceInformation(key, label, ContractCardinality.One);
    contractChoice.setChoices(choices);
    contractChoice.setDefaultValue(List.of(def));
    return contractChoice;
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Choice;
  }
}
