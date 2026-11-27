import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import useRevokeForm from '../../../../../admin/components/threat_arsenal/revocation/useRevokeForm';
import { type PayloadVersionOutput } from '../../../../../utils/api-types';
import { aUsage, aVersion, intlWrapper, someUsages } from '../threatArsenalTestUtils';

const mocks = vi.hoisted(() => ({
  fetchUsages: vi.fn(),
  move: vi.fn(),
  revoke: vi.fn(),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  fetchThreatArsenalUsages: mocks.fetchUsages,
  moveThreatArsenalUsages: mocks.move,
}));
vi.mock('../../../../../actions/payloads/payload-revocation-actions', () => ({ revokePayload: mocks.revoke }));

const v1 = aVersion();
const v2 = aVersion({
  payload_id: 'payload-v2',
  payload_action_id: 'action-v2',
  payload_version: '2',
});
const usages = someUsages([aUsage()]);

const renderForm = (versions: PayloadVersionOutput[] = [v1, v2], currentPayloadId = 'payload-v2') => {
  const onDone = vi.fn();
  const hook = renderHook(() => useRevokeForm({
    open: true,
    versions,
    currentPayloadId,
    onDone,
  }), { wrapper: intlWrapper });
  return {
    ...hook,
    onDone,
  };
};

describe('useRevokeForm', () => {
  beforeEach(() => {
    mocks.fetchUsages.mockResolvedValue({ data: usages });
    mocks.move.mockResolvedValue({
      data: {
        moved_ids: ['usage-1'],
        refused: [],
      },
    });
    mocks.revoke.mockResolvedValue({});
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('given the shown version among the choices should choose it and load its usages', async () => {
    // Act
    const { result } = renderForm();

    // Assert
    await waitFor(() => expect(result.current.usages).toEqual(usages));
    expect(result.current.selected).toBe('payload-v2');
    expect(result.current.version).toBe(v2);
    expect(mocks.fetchUsages).toHaveBeenCalledWith('action-v2');
  });

  it('given the shown version not among the choices should choose the first one', async () => {
    // Act
    const { result } = renderForm([v1]);

    // Assert
    await waitFor(() => expect(result.current.selected).toBe('payload-v1'));
  });

  it('given a revocation without a reason should not submit', async () => {
    // Act
    const { result } = renderForm();

    // Assert
    await waitFor(() => expect(result.current.version).toBe(v2));
    expect(result.current.canSubmit).toBe(false);
  });

  it('given a reason and injects in use should move them to another version, then revoke', async () => {
    // Arrange
    const { result, onDone } = renderForm();
    await waitFor(() => expect(result.current.version).toBe(v2));
    act(() => result.current.setReason('  Flagged by the EDR vendor  '));
    expect(result.current.canSubmit).toBe(true);

    // Act
    await act(() => result.current.revoke());

    // Assert
    expect(mocks.move).toHaveBeenCalledWith('action-v2', expect.objectContaining({ target_payload_id: 'payload-v1' }));
    expect(mocks.revoke).toHaveBeenCalledWith('payload-v2', { payload_revocation_reason: 'Flagged by the EDR vendor' });
    expect(onDone).toHaveBeenCalledWith(v2);
    expect(result.current.submitting).toBe(false);
  });

  it('given a failed revocation should not report it done', async () => {
    // Arrange
    mocks.revoke.mockRejectedValue(new Error('refused'));
    const { result, onDone } = renderForm();
    await waitFor(() => expect(result.current.version).toBe(v2));
    act(() => result.current.setReason('Flagged'));

    // Act
    await act(async () => {
      await expect(result.current.revoke()).rejects.toThrow('refused');
    });

    // Assert
    expect(onDone).not.toHaveBeenCalled();
    expect(result.current.submitting).toBe(false);
  });
});
