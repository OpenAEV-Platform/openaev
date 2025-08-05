package io.openbas.injector_contract;

import io.openbas.injector_contract.fields.ContractElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ContractDef {

  private final List<ContractElement> fields = new ArrayList<>();

  private ContractDef() {
    // private constructor
  }

  public static ContractDef contractBuilder() {
    return new ContractDef();
  }

  /** Adds a mandatory field to the contract */
  public ContractDef mandatory(final ContractElement element) {
    if (element == null) {
      throw new IllegalArgumentException("Field cannot be null");
    }
    this.fields.add(element);
    return this;
  }

  /** Adds an optional field to the contract */
  public ContractDef optional(final ContractElement element) {
    if (element == null) {
      throw new IllegalArgumentException("Field cannot be null");
    }
    element.setMandatory(false);
    this.fields.add(element);
    return this;
  }

  /**
   * Adds a group of fields where at least one must be filled. Each field becomes part of a mutual
   * group of optional fields that together fulfill a mandatory constraint.
   *
   * <p>Example: either "A" or "B" must be filled.
   */
  public ContractDef mandatoryGroup(final ContractElement... elements) {
    if (elements == null || elements.length == 0) {
      throw new IllegalArgumentException("At least one field is required in a mandatory group");
    }

    List<String> keys = Arrays.stream(elements).map(ContractElement::getKey).toList();
    for (ContractElement element : elements) {
      element.setMandatory(false);
      element.setMandatoryGroups(keys);
      this.fields.add(element);
    }
    return this;
  }

  /**
   * Add a field that will be mandatory if another field is set
   *
   * @param element element to be mandatory
   * @param conditionalElement if this field is set the element will be mandatory
   */
  public ContractDef mandatoryOnCondition(
      ContractElement element, ContractElement conditionalElement) {
    if (element == null || conditionalElement == null) {
      throw new IllegalArgumentException("Fields cannot be null");
    }

    element.setMandatoryConditionFields(List.of(conditionalElement.getKey()));
    element.setMandatory(false);
    this.fields.add(element);
    return this;
  }

  /**
   * Add a field that will be mandatory if another field is set with specific value
   *
   * @param element element to be mandatory
   * @param conditionalElement if this field is set with a specific value the element will be
   *     mandatory
   */
  public ContractDef mandatoryOnConditionValue(
      ContractElement element, ContractElement conditionalElement, String value) {
    if (element == null || conditionalElement == null) {
      throw new IllegalArgumentException("Fields cannot be null");
    }

    element.setMandatoryConditionFields(List.of(conditionalElement.getKey()));
    element.setMandatoryConditionValues(Map.of(conditionalElement.getKey(), value));
    element.setMandatory(false);
    this.fields.add(element);
    return this;
  }

  public List<ContractElement> build() {
    return this.fields;
  }
}
