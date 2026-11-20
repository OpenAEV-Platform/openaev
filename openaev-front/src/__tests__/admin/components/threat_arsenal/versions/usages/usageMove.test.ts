import { beforeEach, describe, expect, it, vi } from 'vitest';

import { moveTargets, moveUsages } from '../../../../../../admin/components/threat_arsenal/versions/usages/usageMove';
import { type PayloadUsageOutput, type PayloadVersionOutput } from '../../../../../../utils/api-types';

const mocks = vi.hoisted(() => ({ moveThreatArsenalUsages: vi.fn() }));

vi.mock('../../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ moveThreatArsenalUsages: mocks.moveThreatArsenalUsages }));

const version = (id: string, latest = false, revoked = false): PayloadVersionOutput => ({
  payload_id: id,
  payload_action_id: `action-${id}`,
  payload_version: id,
  payload_is_latest: latest,
  payload_revoked: revoked,
  payload_revoked_by_catalogue: false,
});

describe('moveTargets', () => {
  it('given_revokedAndSourceVersions_should_offerOnlyTheOthers', () => {
    // Arrange
    const versions = [version('3', false, true), version('2', true), version('1')];

    // Act
    const { targets, defaultTarget } = moveTargets(versions, '1');

    // Assert
    expect(targets.map(v => v.payload_id)).toEqual(['2']);
    expect(defaultTarget).toBe('2');
  });

  it('given_noLatestAmongTargets_should_pickTheFirst', () => {
    // Arrange
    const versions = [version('3'), version('2'), version('1', true)];

    // Act
    const { defaultTarget } = moveTargets(versions, '1');

    // Assert
    expect(defaultTarget).toBe('3');
  });

  it('given_noOtherVersion_should_offerNothing', () => {
    // Act
    const { targets, defaultTarget } = moveTargets([version('1', true)], '1');

    // Assert
    expect(targets).toEqual([]);
    expect(defaultTarget).toBe('');
  });
});

describe('moveUsages', () => {
  beforeEach(() => mocks.moveThreatArsenalUsages.mockReset());

  it('given_usages_should_sendTheirIdsToTheTarget', async () => {
    // Arrange
    const moves = {
      moved_ids: ['inject-1'],
      refused: [],
    };
    mocks.moveThreatArsenalUsages.mockResolvedValue({ data: moves });
    const usages = [{
      usage_id: 'inject-1',
      usage_type: 'inject',
    }, {
      usage_id: 'step-1',
      usage_type: 'step',
    }] as PayloadUsageOutput[];

    // Act
    const result = await moveUsages('action-1', 'payload-2', usages);

    // Assert
    expect(mocks.moveThreatArsenalUsages).toHaveBeenCalledWith('action-1', {
      target_payload_id: 'payload-2',
      usage_ids: ['inject-1', 'step-1'],
    });
    expect(result).toEqual(moves);
  });
});
