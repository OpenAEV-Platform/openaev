package io.openaev.injector_contract.fields;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ARTICLES;

import io.openaev.injector_contract.ContractCardinality;

public class ContractArticle extends ContractCardinalityElement {

  public ContractArticle(ContractCardinality cardinality) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_ARTICLES, "Articles", cardinality);
  }

  public static ContractArticle articleField(ContractCardinality cardinality) {
    return new ContractArticle(cardinality);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Article;
  }
}
