import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import useNewVersionMove from '../../../../../admin/components/threat_arsenal/versions/useNewVersionMove';
import { type ThreatArsenalAction } from '../../../../../utils/api-types';
import { actionId, olderVersion, payloadId, usage, versionAction } from './payloadVersionFixtures';

const mocks = vi.hoisted(() => ({
  moveUsages: vi.fn(),
  notifyMoves: vi.fn(),
}));

vi.mock('../../../../../admin/components/threat_arsenal/versions/usages/usageMove', () => ({ moveUsages: mocks.moveUsages }));
vi.mock('../../../../../admin/components/threat_arsenal/versions/usages/useNotifyMoves', () => ({ default: () => mocks.notifyMoves }));

const firstVersion = olderVersion('1.0.0', usage('inject-1'), usage('step-1', undefined, 'step'));
const secondVersion = olderVersion('1.1.0', usage('inject-2'));

const renderMove = (action: ThreatArsenalAction = versionAction('1.2.0')) => renderHook(() => useNewVersionMove(action, [firstVersion, secondVersion])).result;

describe('useNewVersionMove', () => {
  beforeEach(() => {
    mocks.moveUsages.mockReset();
    mocks.notifyMoves.mockReset();
  });

  describe('selection', () => {
    it('given_noChoice_should_selectNothing', () => {
      // Act
      const result = renderMove();

      // Assert
      expect(result.current.selectedCount).toBe(0);
      expect(result.current.totalCount).toBe(3);
      expect(result.current.allSelected).toBe(false);
    });

    it('given_versionToggled_should_selectThenClearItsUsages', () => {
      // Arrange
      const result = renderMove();

      // Act
      act(() => result.current.toggleVersion(firstVersion));

      // Assert
      expect(result.current.selectedIn(firstVersion)).toBe(2);
      expect(result.current.selectedIn(secondVersion)).toBe(0);
      act(() => result.current.toggleVersion(firstVersion));
      expect(result.current.selectedCount).toBe(0);
    });

    it('given_selectAll_should_selectEveryUsageUntilCleared', () => {
      // Arrange
      const result = renderMove();

      // Act
      act(() => result.current.selectAll());

      // Assert
      expect(result.current.allSelected).toBe(true);
      expect(result.current.selectedCount).toBe(3);
      act(() => result.current.clear());
      expect(result.current.selectedCount).toBe(0);
    });
  });

  describe('move', () => {
    it('given_oneVersionChosen_should_moveOnlyItsUsagesAndNotify', async () => {
      // Arrange
      const moved = {
        moved_ids: ['inject-1', 'step-1'],
        refused: [],
      };
      mocks.moveUsages.mockResolvedValue(moved);
      const result = renderMove();
      act(() => result.current.toggleVersion(firstVersion));

      // Act
      let done: boolean | undefined;
      await act(async () => {
        done = await result.current.move();
      });

      // Assert
      expect(done).toBe(true);
      expect(mocks.moveUsages).toHaveBeenCalledTimes(1);
      expect(mocks.moveUsages).toHaveBeenCalledWith(actionId('1.0.0'), payloadId('1.2.0'), firstVersion.usages.payload_usages);
      expect(mocks.notifyMoves).toHaveBeenCalledWith([moved], '1.2.0');
      expect(result.current.moving).toBe(false);
    });

    it('given_moveFails_should_resolveFalse', async () => {
      // Arrange
      mocks.moveUsages.mockRejectedValue(new Error('Refused'));
      const result = renderMove();
      act(() => result.current.selectAll());

      // Act
      let done: boolean | undefined;
      await act(async () => {
        done = await result.current.move();
      });

      // Assert
      expect(done).toBe(false);
      expect(mocks.notifyMoves).not.toHaveBeenCalled();
      expect(result.current.moving).toBe(false);
    });

    it('given_actionWithoutPayload_should_moveNothing', async () => {
      // Arrange
      const result = renderMove({ injector_contract_id: actionId('1.2.0') } as ThreatArsenalAction);
      act(() => result.current.selectAll());

      // Act
      let done: boolean | undefined;
      await act(async () => {
        done = await result.current.move();
      });

      // Assert
      expect(done).toBe(false);
      expect(mocks.moveUsages).not.toHaveBeenCalled();
    });
  });
});
