package io.openaev.injector_contract.fields;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_CHALLENGES;

import io.openaev.injector_contract.ContractCardinality;

/**
 * Contract element representing a challenge selector field.
 *
 * <p>Challenge fields allow users to select challenges that targets must complete as part of the
 * injection. This is used for training and skill verification scenarios.
 *
 * @see ContractCardinalityElement
 */
public class ContractChallenge extends ContractCardinalityElement {

  /**
   * Creates a new challenge field with the specified cardinality.
   *
   * @param cardinality whether to allow single or multiple challenge selection
   */
  public ContractChallenge(ContractCardinality cardinality) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_CHALLENGES, "Challenges", cardinality);
  }

  /**
   * Creates a challenge selector field.
   *
   * @param cardinality whether to allow single or multiple selection
   * @return a configured ContractChallenge instance
   */
  public static ContractChallenge challengeField(ContractCardinality cardinality) {
    return new ContractChallenge(cardinality);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Challenge;
  }
}
