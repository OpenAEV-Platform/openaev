import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import useAfterNewVersion from '../../../../../admin/components/threat_arsenal/versions/useAfterNewVersion';
import { actionId, versionAction } from './payloadVersionFixtures';

const mocks = vi.hoisted(() => ({ olderVersionsInUse: vi.fn() }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalOlderVersionsInUse: mocks.olderVersionsInUse }));

const SAVED_ID = actionId('1.0.0');

const renderAfterSave = () => {
  const onUpdate = vi.fn();
  const onShowSaved = vi.fn();
  const { result } = renderHook(() => useAfterNewVersion(SAVED_ID, onUpdate, onShowSaved));
  return {
    afterSave: result.current,
    onUpdate,
    onShowSaved,
  };
};

describe('useAfterNewVersion', () => {
  beforeEach(() => {
    mocks.olderVersionsInUse.mockReset();
  });

  it('given_saveInPlace_should_updateTheCardRightAway', () => {
    // Arrange
    const { afterSave, onUpdate, onShowSaved } = renderAfterSave();

    // Act
    act(() => afterSave(versionAction('1.0.0')));

    // Assert
    expect(onUpdate).toHaveBeenCalledWith(versionAction('1.0.0'), SAVED_ID);
    expect(onShowSaved).not.toHaveBeenCalled();
    expect(mocks.olderVersionsInUse).not.toHaveBeenCalled();
  });

  it('given_injectsOnOlderVersions_should_replaceTheCardAndOpenItsVersions', async () => {
    // Arrange
    mocks.olderVersionsInUse.mockResolvedValue({ data: { [actionId('1.1.0')]: 2 } });
    const { afterSave, onUpdate, onShowSaved } = renderAfterSave();

    // Act
    act(() => afterSave(versionAction('1.1.0')));

    // Assert
    await waitFor(() => expect(onShowSaved).toHaveBeenCalledWith(versionAction('1.1.0')));
    expect(onUpdate).toHaveBeenCalledWith(versionAction('1.1.0'), SAVED_ID);
    expect(mocks.olderVersionsInUse).toHaveBeenCalledWith({ action_ids: [actionId('1.1.0')] });
  });

  it('given_noInjectOnOlderVersions_should_replaceTheCardOnly', async () => {
    // Arrange
    mocks.olderVersionsInUse.mockResolvedValue({ data: {} });
    const { afterSave, onUpdate, onShowSaved } = renderAfterSave();

    // Act
    act(() => afterSave(versionAction('1.1.0')));

    // Assert
    await waitFor(() => expect(onUpdate).toHaveBeenCalledWith(versionAction('1.1.0'), SAVED_ID));
    expect(onShowSaved).not.toHaveBeenCalled();
  });

  it('given_countStillLoading_should_wait', () => {
    // Arrange
    mocks.olderVersionsInUse.mockReturnValue(new Promise(() => undefined));
    const { afterSave, onUpdate, onShowSaved } = renderAfterSave();

    // Act
    act(() => afterSave(versionAction('1.1.0')));

    // Assert
    expect(onUpdate).not.toHaveBeenCalled();
    expect(onShowSaved).not.toHaveBeenCalled();
  });
});
