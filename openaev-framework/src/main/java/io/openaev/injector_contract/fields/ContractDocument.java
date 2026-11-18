package io.openaev.injector_contract.fields;

import lombok.Getter;
import lombok.Setter;

/**
 * Contract element representing a document selector.
 *
 * <p>Used for a payload argument of type document: the inject form renders a document picker and
 * stores the selected document id under the field key. The default value is the id of the
 * argument's default document.
 *
 * @see ContractElement
 */
@Setter
@Getter
public class ContractDocument extends ContractElement {

  /** Id of the document selected by default. */
  private String defaultValue = "";

  /**
   * Creates a new document field.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   */
  public ContractDocument(String key, String label) {
    super(key, label);
  }

  /**
   * Creates a document field with a default document.
   *
   * @param key the unique identifier for this field
   * @param label the display label
   * @param defaultValue the id of the default document
   * @return a configured ContractDocument instance
   */
  public static ContractDocument documentField(String key, String label, String defaultValue) {
    ContractDocument contractDocument = new ContractDocument(key, label);
    contractDocument.setDefaultValue(defaultValue);
    return contractDocument;
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Document;
  }
}
