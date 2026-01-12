package io.openaev.injector_contract.fields;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TEAMS;

import io.openaev.injector_contract.ContractCardinality;

/**
 * Contract element representing a team selector field.
 *
 * <p>Team fields allow users to select teams whose members will be targeted by the injection. This
 * is commonly used for human-targeted injections like phishing or awareness campaigns.
 *
 * @see ContractCardinalityElement
 */
public class ContractTeam extends ContractCardinalityElement {

  /**
   * Creates a new team field with the specified cardinality.
   *
   * @param cardinality whether to allow single or multiple team selection
   */
  public ContractTeam(ContractCardinality cardinality) {
    super(CONTRACT_ELEMENT_CONTENT_KEY_TEAMS, "Teams", cardinality);
  }

  /**
   * Creates a team selector field.
   *
   * @param cardinality whether to allow single or multiple selection
   * @return a configured ContractTeam instance
   */
  public static ContractTeam teamField(ContractCardinality cardinality) {
    return new ContractTeam(cardinality);
  }

  @Override
  public ContractFieldType getType() {
    return ContractFieldType.Team;
  }
}
