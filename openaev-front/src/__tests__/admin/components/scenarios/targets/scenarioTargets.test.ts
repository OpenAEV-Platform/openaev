import { describe, expect, it } from 'vitest';

import { NO_TARGETS, toTargetsInput } from '../../../../../admin/components/scenarios/targets/scenarioTargets';

describe('toTargetsInput', () => {
  it('sends the ids of the chosen options and one manual target per word', () => {
    // Arrange
    const targets = {
      assets: [{
        id: 'asset-id',
        label: 'Asset',
      }],
      assetGroups: [{
        id: 'group-id',
        label: 'Group',
      }],
      teams: [{
        id: 'team-id',
        label: 'Team',
      }],
      manual: ' 10.0.0.1, host.local;\nexample.org ',
    };

    // Act
    const input = toTargetsInput(targets);

    // Assert
    expect(input).toEqual({
      asset_ids: ['asset-id'],
      asset_group_ids: ['group-id'],
      team_ids: ['team-id'],
      manual_targets: ['10.0.0.1', 'host.local', 'example.org'],
    });
  });

  it('sends empty lists when nothing is chosen', () => {
    // Act
    const input = toTargetsInput(NO_TARGETS);

    // Assert
    expect(input).toEqual({
      asset_ids: [],
      asset_group_ids: [],
      team_ids: [],
      manual_targets: [],
    });
  });
});
