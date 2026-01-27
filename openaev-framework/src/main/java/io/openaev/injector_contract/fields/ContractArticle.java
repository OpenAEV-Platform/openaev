package io.openaev.injector_contract.fields;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ARTICLES;

import io.openaev.injector_contract.ContractCardinality;

/**
 * Contract element representing an article/channel content selector field.
 *
 * <p>Article fields allow users to select existing articles or channel content to be referenced in
 * the injection. This is commonly used for channel injections where users read specific content.
 *
 * @see ContractCardinalityElement
 */
public class ContractArticle extends ContractCardinalityElement {

  /**
   * Creates a new article field with the specified cardinality.
   *
   * @param cardinality whether to allow single or multiple article selection
   */
  public ContractArticle(ContractCardinality cardinality) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_ARTICLES, "Articles", cardinality);
  }

  /**
   * Creates an article selector field.
   *
   * @param cardinality whether to allow single or multiple selection
   * @return a configured ContractArticle instance
   */
  public static ContractArticle articleField(ContractCardinality cardinality) {
    return new ContractArticle(cardinality);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Article;
  }
}
