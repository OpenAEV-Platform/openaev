import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import useNotifyMoves from '../../../../../../admin/components/threat_arsenal/versions/usages/useNotifyMoves';
import { type PayloadUsageMoveOutput } from '../../../../../../utils/api-types';

const mocks = vi.hoisted(() => ({
  notifyError: vi.fn(),
  notifySuccess: vi.fn(),
}));

vi.mock('../../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (message: string, values?: Record<string, string>) => message.replace(/\{(\w+)\}/g, (_, key: string) => values?.[key] ?? key) }) }));
vi.mock('../../../../../../utils/Environment', () => ({
  MESSAGING$: {
    notifyError: mocks.notifyError,
    notifySuccess: mocks.notifySuccess,
  },
}));

const result = (moved: string[], refused: string[] = []) => ({
  moved_ids: moved,
  refused: refused.map(id => ({ usage_id: id })),
}) as unknown as PayloadUsageMoveOutput;

describe('useNotifyMoves', () => {
  beforeEach(() => vi.clearAllMocks());

  it('given every usage moved should count them all', () => {
    // Arrange
    const notifyMoves = renderHook(() => useNotifyMoves()).result.current;

    // Act
    notifyMoves([result(['a', 'b']), result(['c'])], '1.2.0');

    // Assert
    expect(mocks.notifySuccess).toHaveBeenCalledWith('3 usages moved to v1.2.0');
    expect(mocks.notifyError).not.toHaveBeenCalled();
  });

  it('given usages refused should say they stay on the previous version', () => {
    // Arrange
    const notifyMoves = renderHook(() => useNotifyMoves()).result.current;

    // Act
    notifyMoves([result(['a'], ['b']), result([], ['c'])], '1.2.0');

    // Assert
    expect(mocks.notifyError).toHaveBeenCalledWith('2 usages could not move and stay on the previous version');
    expect(mocks.notifySuccess).not.toHaveBeenCalled();
  });
});
