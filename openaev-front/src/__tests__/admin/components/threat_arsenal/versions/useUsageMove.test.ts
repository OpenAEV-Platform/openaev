import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import useUsageMove from '../../../../../admin/components/threat_arsenal/versions/useUsageMove';
import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../../utils/api-types';

const mocks = vi.hoisted(() => ({ moveThreatArsenalUsages: vi.fn() }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ moveThreatArsenalUsages: mocks.moveThreatArsenalUsages }));

const version = (id: string, latest = false): PayloadVersionOutput => ({
  payload_id: id,
  payload_action_id: `action-${id}`,
  payload_version: id,
  payload_is_latest: latest,
  payload_revoked: false,
  payload_revoked_by_catalogue: false,
});

const V1 = version('1');
const V2 = version('2', true);
const USAGES: PayloadUsagesOutput = {
  payload_usages: [{
    usage_id: 'inject-1',
    usage_type: 'inject',
    usage_name: 'Dump credentials',
  }, {
    usage_id: 'step-1',
    usage_type: 'step',
    usage_name: 'Chained dump',
  }],
  payload_usages_hidden_count: 0,
};

describe('useUsageMove', () => {
  beforeEach(() => mocks.moveThreatArsenalUsages.mockReset());

  it('given_nothingPicked_should_notAllowTheMove', () => {
    // Act
    const { result } = renderHook(() => useUsageMove({
      source: V1,
      versions: [V2, V1],
      usages: USAGES,
      onMoved: vi.fn(),
    }));

    // Assert
    expect(result.current.target).toBe('2');
    expect(result.current.chosenCount).toBe(0);
    expect(result.current.canMove).toBe(false);
  });

  it('given_everyUsagePickedButOne_should_moveTheOthersAndNameTheRefused', async () => {
    // Arrange
    mocks.moveThreatArsenalUsages.mockResolvedValue({
      data: {
        moved_ids: [],
        refused: [{
          usage_id: 'step-1',
          reason: 'MISSING_RIGHT',
        }],
      },
    });
    const onMoved = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() => useUsageMove({
      source: V1,
      versions: [V2, V1],
      usages: USAGES,
      onMoved,
    }));
    act(() => result.current.selection.handleToggleSelectAll());
    act(() => result.current.selection.onToggleEntity(USAGES.payload_usages[0]));

    // Act
    await act(() => result.current.move());

    // Assert
    expect(mocks.moveThreatArsenalUsages).toHaveBeenCalledWith('action-1', {
      target_payload_id: '2',
      usage_ids: ['step-1'],
    });
    expect(onMoved).toHaveBeenCalled();
    await waitFor(() => expect(result.current.result?.refused).toHaveLength(1));
    expect(result.current.nameOf('step-1')).toBe('Chained dump');
    expect(result.current.moving).toBe(false);
  });
});
