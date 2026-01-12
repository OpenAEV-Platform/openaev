package io.openaev.model;

import io.openaev.injector_contract.fields.ContractElement;
import io.openaev.injector_contract.fields.ContractFieldType;
import lombok.Getter;
import lombok.Setter;

/**
 * Model representing a link between contract fields.
 *
 * <p>Linked fields establish dependencies between contract elements, allowing one field's value to
 * influence or provide options for another field. Common use cases include:
 *
 * <ul>
 *   <li>A targeted property field linked to a targeted asset field, enabling selection of IP,
 *       hostname, or other properties from the selected asset
 *   <li>Dynamic choice fields that update based on another field's selection
 * </ul>
 *
 * <p>Use the factory method {@link #fromField(ContractElement)} to create instances from contract
 * elements.
 *
 * @see io.openaev.injector_contract.fields.ContractElement#setLinkedFields(java.util.List)
 */
@Getter
@Setter
public class LinkedFieldModel {

  /** The unique key identifying the linked field. */
  private String key;

  /** The type of the linked field. */
  private ContractFieldType type;

  private LinkedFieldModel(String key, ContractFieldType type) {
    this.key = key;
    this.type = type;
  }

  /**
   * Creates a LinkedFieldModel from a contract element.
   *
   * @param fieldContract the contract element to create a link model from
   * @return a new LinkedFieldModel with the element's key and type
   * @throws NullPointerException if fieldContract is null
   */
  public static LinkedFieldModel fromField(ContractElement fieldContract) {
    if (fieldContract == null) {
      throw new NullPointerException("fieldContract cannot be null");
    }
    return new LinkedFieldModel(fieldContract.getKey(), fieldContract.getType());
  }
}
