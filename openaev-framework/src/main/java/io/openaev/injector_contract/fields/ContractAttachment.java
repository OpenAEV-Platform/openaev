package io.openaev.injector_contract.fields;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ATTACHMENTS;

import io.openaev.injector_contract.ContractCardinality;

/**
 * Contract element representing a file attachment field.
 *
 * <p>Attachment fields allow users to upload and attach files to an injection. Commonly used for
 * attaching documents, images, or other files to be included in the injection payload.
 *
 * @see ContractCardinalityElement
 */
public class ContractAttachment extends ContractCardinalityElement {

  /**
   * Creates a new attachment field with the specified cardinality.
   *
   * @param cardinality whether to allow single or multiple attachments
   */
  public ContractAttachment(ContractCardinality cardinality) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_ATTACHMENTS, "Attachments", cardinality);
  }

  /**
   * Creates an attachment field.
   *
   * @param cardinality whether to allow single or multiple attachments
   * @return a configured ContractAttachment instance
   */
  public static ContractAttachment attachmentField(ContractCardinality cardinality) {
    return new ContractAttachment(cardinality);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Attachment;
  }
}
