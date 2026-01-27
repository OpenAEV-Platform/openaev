package io.openaev.injector_contract;

import io.openaev.injector_contract.fields.ContractElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Builder class for constructing contract field definitions.
 *
 * <p>This class provides a fluent API for defining the fields that make up an injection contract
 * form. It supports various field requirement patterns:
 *
 * <ul>
 *   <li>{@link #mandatory(ContractElement)} - Always required fields
 *   <li>{@link #optional(ContractElement)} - Optional fields
 *   <li>{@link #mandatoryGroup(ContractElement...)} - At least one must be filled
 *   <li>{@link #mandatoryOnCondition(ContractElement, ContractElement)} - Required when another
 *       field is set
 *   <li>{@link #mandatoryOnConditionValue(ContractElement, ContractElement, String)} - Required
 *       when another field has specific value
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * List<ContractElement> fields = ContractDef.contractBuilder()
 *     .mandatory(ContractText.textField("subject", "Subject"))
 *     .optional(ContractText.textField("body", "Body"))
 *     .mandatoryGroup(emailField, phoneField)
 *     .build();
 * }</pre>
 *
 * @see ContractElement
 * @see Contract
 */
public class ContractDef {

  private final List<ContractElement> fields = new ArrayList<>();

  private ContractDef() {
    // private constructor
  }

  /**
   * Creates a new contract definition builder.
   *
   * @return a new ContractDef instance
   */
  public static ContractDef contractBuilder() {
    return new ContractDef();
  }

  /**
   * Adds a mandatory field to the contract.
   *
   * @param element the field to add (must not be null)
   * @return this builder for method chaining
   * @throws IllegalArgumentException if element is null
   */
  public ContractDef mandatory(final ContractElement element) {
    if (element == null) {
      throw new IllegalArgumentException("Field cannot be null");
    }
    this.fields.add(element);
    return this;
  }

  /**
   * Adds an optional field to the contract.
   *
   * @param element the field to add (must not be null)
   * @return this builder for method chaining
   * @throws IllegalArgumentException if element is null
   */
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
   * Adds a field that becomes mandatory when another field is set.
   *
   * <p>This creates a conditional validation rule where filling in the conditional field triggers
   * the requirement for this field.
   *
   * @param element the field that becomes mandatory when the condition is met
   * @param conditionalElement the field that triggers the mandatory condition when set
   * @return this builder for method chaining
   * @throws IllegalArgumentException if either element is null
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
   * Adds a field that becomes mandatory when another field has a specific value.
   *
   * <p>This creates a conditional validation rule where setting the conditional field to the
   * specified value triggers the requirement for this field.
   *
   * @param element the field that becomes mandatory when the condition is met
   * @param conditionalElement the field whose value triggers the mandatory condition
   * @param value the value that triggers the mandatory condition
   * @return this builder for method chaining
   * @throws IllegalArgumentException if element or conditionalElement is null
   */
  public ContractDef mandatoryOnConditionValue(
      ContractElement element, ContractElement conditionalElement, String value) {
    return setMandatoryOnCondition(element, conditionalElement, value);
  }

  /**
   * Adds a field that is mandatory when another field has any of the specified values.
   *
   * @param element the field that becomes mandatory
   * @param conditionalElement the field that triggers the mandatory condition
   * @param values the values that trigger the mandatory condition
   * @return this builder for method chaining
   * @throws IllegalArgumentException if any field is null
   */
  public ContractDef mandatoryOnConditionValue(
      ContractElement element, ContractElement conditionalElement, List<String> values) {
    return setMandatoryOnCondition(element, conditionalElement, values);
  }

  private ContractDef setMandatoryOnCondition(
      ContractElement element, ContractElement conditionalElement, Object values) {
    if (element == null || conditionalElement == null) {
      throw new IllegalArgumentException("Fields cannot be null");
    }

    element.setMandatoryConditionFields(List.of(conditionalElement.getKey()));
    element.setMandatoryConditionValues(Map.of(conditionalElement.getKey(), values));
    element.setMandatory(false);
    this.fields.add(element);
    return this;
  }

  /**
   * Builds and returns the list of configured contract elements.
   *
   * @return an unmodifiable list of contract elements
   */
  public List<ContractElement> build() {
    return this.fields;
  }
}
