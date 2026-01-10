package io.openaev.injector_contract.fields;

import lombok.Getter;
import lombok.Setter;

/**
 * Contract element representing a numeric input field.
 *
 * <p>Number fields accept integer or decimal values. They are used for quantities, durations,
 * scores, or other numeric configurations.
 *
 * @see ContractElement
 */
@Setter
@Getter
public class ContractNumber extends ContractElement {

  /** Default numeric value to pre-populate the field. */
  private Number defaultValue;

  /**
   * Creates a new number field.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   */
  public ContractNumber(String key, String label) {
    super(key, label);
  }

  /**
   * Creates a number field with no default value.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @return a configured ContractNumber instance
   */
  public static ContractNumber numberField(String key, String label) {
    return new ContractNumber(key, label);
  }

  /**
   * Creates a number field with a default value.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param defaultValue the initial value
   * @return a configured ContractNumber instance
   */
  public static ContractNumber numberField(String key, String label, Number defaultValue) {
    ContractNumber contractNumber = new ContractNumber(key, label);
    contractNumber.setDefaultValue(defaultValue);
    return contractNumber;
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Number;
  }
}
