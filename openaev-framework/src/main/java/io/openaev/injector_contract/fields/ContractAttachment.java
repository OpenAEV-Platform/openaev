package io.openaev.injector_contract.fields;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ATTACHMENTS;

import io.openaev.injector_contract.ContractCardinality;

public class ContractAttachment extends ContractCardinalityElement {

  public ContractAttachment(ContractCardinality cardinality) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_ATTACHMENTS, "Attachments", cardinality);
  }

  public static ContractAttachment attachmentField(ContractCardinality cardinality) {
    return new ContractAttachment(cardinality);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Attachment;
  }
}
