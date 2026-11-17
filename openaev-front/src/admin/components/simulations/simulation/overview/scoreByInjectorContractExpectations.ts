import { type InjectExpectationOutput } from '../../../../../utils/api-types';

/**
 * Whether an inject expectation is a top-level row of its inject, the level the score
 * charts sum up, for tabletop and technical injects alike (#8396).
 *
 * - Tabletop: the team row (no player). Player rows roll up into it.
 * - Technical: the asset group row (no asset, no agent), or the asset row of an asset
 *   targeted directly (no asset group, no agent). Agent rows roll up into their asset,
 *   and the asset rows of a group roll up into the group, so counting them as well would
 *   count the same result twice.
 *
 * Expectations with no score yet are left out, as the chart plots scores over time.
 */
export const isTopLevelScoredExpectation = (expectation: InjectExpectationOutput): boolean => {
  if (expectation.inject_expectation_score == null) {
    return false;
  }
  if (expectation.inject_expectation_team) {
    return expectation.inject_expectation_user == null;
  }
  if (expectation.inject_expectation_agent != null) {
    return false;
  }
  if (expectation.inject_expectation_asset_group != null) {
    return expectation.inject_expectation_asset == null;
  }
  return expectation.inject_expectation_asset != null;
};

export default isTopLevelScoredExpectation;
