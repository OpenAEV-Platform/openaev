import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import useRevocationForm from '../../../../../admin/components/threat_arsenal/revocation/useRevocationForm';
import { type PayloadVersionOutput } from '../../../../../utils/api-types';
import { aUsage, aVersion, someUsages } from '../threatArsenalTestUtils';

const mocks = vi.hoisted(() => ({
  fetchUsages: vi.fn(),
  revoke: vi.fn(),
  lift: vi.fn(),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalUsages: mocks.fetchUsages }));
vi.mock('../../../../../actions/payloads/payload-revocation-actions', () => ({
  revokePayload: mocks.revoke,
  liftPayloadRevocation: mocks.lift,
}));

const v1 = aVersion();
const v2 = aVersion({
  payload_id: 'payload-v2',
  payload_action_id: 'action-v2',
  payload_version: '2',
});
const usages = someUsages([aUsage()]);

const renderForm = (lift: boolean, versions: PayloadVersionOutput[] = [v1, v2], currentPayloadId = 'payload-v2') => {
  const onDone = vi.fn();
  const hook = renderHook(() => useRevocationForm({
    open: true,
    lift,
    versions,
    currentPayloadId,
    onDone,
  }));
  return {
    ...hook,
    onDone,
  };
};

describe('useRevocationForm', () => {
  beforeEach(() => {
    mocks.fetchUsages.mockResolvedValue({ data: usages });
    mocks.revoke.mockResolvedValue({});
    mocks.lift.mockResolvedValue({});
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('given the shown version among the choices should choose it and load its usages', async () => {
    // Act
    const { result } = renderForm(false);

    // Assert
    await waitFor(() => expect(result.current.usages).toEqual(usages));
    expect(result.current.selected).toBe('payload-v2');
    expect(result.current.version).toBe(v2);
    expect(mocks.fetchUsages).toHaveBeenCalledWith('action-v2');
  });

  it('given the shown version not among the choices should choose the first one', async () => {
    // Act
    const { result } = renderForm(false, [v1]);

    // Assert
    await waitFor(() => expect(result.current.selected).toBe('payload-v1'));
  });

  it('given a revocation without a reason should not submit', async () => {
    // Act
    const { result } = renderForm(false);

    // Assert
    await waitFor(() => expect(result.current.version).toBe(v2));
    expect(result.current.canSubmit).toBe(false);
  });

  it('given a reason should revoke the chosen version with it', async () => {
    // Arrange
    const { result, onDone } = renderForm(false);
    await waitFor(() => expect(result.current.version).toBe(v2));
    act(() => result.current.setReason('  Flagged by the EDR vendor  '));
    expect(result.current.canSubmit).toBe(true);

    // Act
    await act(() => result.current.submit());

    // Assert
    expect(mocks.revoke).toHaveBeenCalledWith('payload-v2', { payload_revocation_reason: 'Flagged by the EDR vendor' });
    expect(onDone).toHaveBeenCalledWith(v2);
    expect(result.current.submitting).toBe(false);
  });

  it('given a lift should lift the chosen version without a reason', async () => {
    // Arrange
    const { result, onDone } = renderForm(true);
    await waitFor(() => expect(result.current.canSubmit).toBe(true));
    act(() => result.current.setSelected('payload-v1'));

    // Act
    await act(() => result.current.submit());

    // Assert
    expect(mocks.lift).toHaveBeenCalledWith('payload-v1');
    expect(mocks.revoke).not.toHaveBeenCalled();
    expect(onDone).toHaveBeenCalledWith(v1);
  });

  it('given a failed revocation should not report it done', async () => {
    // Arrange
    mocks.lift.mockRejectedValue(new Error('refused'));
    const { result, onDone } = renderForm(true);
    await waitFor(() => expect(result.current.canSubmit).toBe(true));

    // Act
    await act(async () => {
      await expect(result.current.submit()).rejects.toThrow('refused');
    });

    // Assert
    expect(onDone).not.toHaveBeenCalled();
    expect(result.current.submitting).toBe(false);
  });
});
