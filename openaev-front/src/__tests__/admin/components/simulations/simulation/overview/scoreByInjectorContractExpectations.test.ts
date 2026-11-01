import { describe, expect, it } from 'vitest';

import { isTopLevelScoredExpectation } from '../../../../../../admin/components/simulations/simulation/overview/scoreByInjectorContractExpectations';
import { type InjectExpectationOutput } from '../../../../../../utils/api-types';

const expectation = (overrides: Partial<InjectExpectationOutput>): InjectExpectationOutput => ({
  inject_expectation_id: 'expectation-1',
  inject_expectation_type: 'DETECTION',
  inject_expectation_expected_score: 100,
  inject_expiration_time: 3600,
  inject_expectation_group: false,
  inject_expectation_score: 100,
  ...overrides,
} as InjectExpectationOutput);

describe('isTopLevelScoredExpectation (#8396)', () => {
  it('keeps the team row of a tabletop inject and drops its player rows', () => {
    expect(isTopLevelScoredExpectation(expectation({ inject_expectation_type: 'MANUAL', inject_expectation_team: 'team-1' }))).toBe(true);
    expect(isTopLevelScoredExpectation(expectation({
      inject_expectation_type: 'MANUAL',
      inject_expectation_team: 'team-1',
      inject_expectation_user: 'user-1',
    }))).toBe(false);
  });

  it('keeps the asset group row of a technical inject and drops its asset and agent rows', () => {
    expect(isTopLevelScoredExpectation(expectation({ inject_expectation_asset_group: 'group-1' }))).toBe(true);
    expect(isTopLevelScoredExpectation(expectation({
      inject_expectation_asset_group: 'group-1',
      inject_expectation_asset: 'asset-1',
    }))).toBe(false);
    expect(isTopLevelScoredExpectation(expectation({
      inject_expectation_asset_group: 'group-1',
      inject_expectation_asset: 'asset-1',
      inject_expectation_agent: 'agent-1',
    }))).toBe(false);
  });

  it('keeps the asset row of an asset targeted directly and drops its agent rows', () => {
    expect(isTopLevelScoredExpectation(expectation({ inject_expectation_asset: 'asset-1' }))).toBe(true);
    expect(isTopLevelScoredExpectation(expectation({
      inject_expectation_asset: 'asset-1',
      inject_expectation_agent: 'agent-1',
    }))).toBe(false);
  });

  it('drops an expectation with no score yet', () => {
    expect(isTopLevelScoredExpectation(expectation({ inject_expectation_asset: 'asset-1', inject_expectation_score: undefined }))).toBe(false);
    expect(isTopLevelScoredExpectation(expectation({
      inject_expectation_type: 'MANUAL',
      inject_expectation_team: 'team-1',
      inject_expectation_score: undefined,
    }))).toBe(false);
  });

  it('drops an expectation with no target at all', () => {
    expect(isTopLevelScoredExpectation(expectation({}))).toBe(false);
  });
});
