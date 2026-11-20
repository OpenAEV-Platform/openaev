import { cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { isXtmComposerIsReachable } from '../../../../../actions/catalog/catalog-actions';
import useXtmComposerReachable from '../../../../../admin/components/integrations/common/useXtmComposerReachable';

vi.mock('../../../../../actions/catalog/catalog-actions', () => ({ isXtmComposerIsReachable: vi.fn() }));

describe('useXtmComposerReachable', () => {
  afterEach(() => {
    cleanup();
    vi.resetAllMocks();
  });

  it('given_theProbeRuns_should_returnNull', () => {
    // Arrange
    vi.mocked(isXtmComposerIsReachable).mockReturnValue(new Promise(() => {}));

    // Act
    const { result } = renderHook(() => useXtmComposerReachable());

    // Assert
    expect(result.current).toBeNull();
  });

  it.each([true, false])('given_theProbeAnswers_%s_should_returnIt', async (reachable) => {
    // Arrange
    vi.mocked(isXtmComposerIsReachable).mockResolvedValue({ data: reachable } as Awaited<ReturnType<typeof isXtmComposerIsReachable>>);

    // Act
    const { result } = renderHook(() => useXtmComposerReachable());

    // Assert
    await waitFor(() => expect(result.current).toBe(reachable));
  });

  it('given_theProbeFails_should_returnFalse', async () => {
    // Arrange
    vi.mocked(isXtmComposerIsReachable).mockRejectedValue(new Error('down'));

    // Act
    const { result } = renderHook(() => useXtmComposerReachable());

    // Assert
    await waitFor(() => expect(result.current).toBe(false));
    expect(isXtmComposerIsReachable).toHaveBeenCalledOnce();
  });
});
