import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import useAfterNewVersion from '../../../../../admin/components/threat_arsenal/versions/useAfterNewVersion';
import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../../utils/api-types';
import { actionId, payloadId, payloadVersion, usage, usagesOf, versionAction } from './payloadVersionFixtures';

const mocks = vi.hoisted(() => ({
  usePayloadVersions: vi.fn(),
  useUsagesByVersion: vi.fn(),
}));

vi.mock('../../../../../admin/components/threat_arsenal/versions/usePayloadVersions', () => ({ default: mocks.usePayloadVersions }));
vi.mock('../../../../../admin/components/threat_arsenal/versions/usages/useUsagesByVersion', () => ({ default: mocks.useUsagesByVersion }));

const SAVED_ID = actionId('1.0.0');

// The versions the new action's payload has once loaded, and the usages of each one.
const versionsLoaded = (usagesByVersion: Record<string, PayloadUsagesOutput>) => {
  mocks.usePayloadVersions.mockImplementation((_id: string, load: boolean) => (load ? Object.keys(usagesByVersion).map(payloadVersion) : null));
  mocks.useUsagesByVersion.mockImplementation((versions: PayloadVersionOutput[] | null) => (versions
    ? Object.fromEntries(Object.entries(usagesByVersion).map(([version, usages]) => [payloadId(version), usages]))
    : null));
};

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
    mocks.usePayloadVersions.mockReset();
    mocks.useUsagesByVersion.mockReset();
  });

  it('given_saveInPlace_should_updateTheCardRightAway', () => {
    // Arrange
    versionsLoaded({});
    const { afterSave, onUpdate, onShowSaved } = renderAfterSave();

    // Act
    act(() => afterSave(versionAction('1.0.0')));

    // Assert
    expect(onUpdate).toHaveBeenCalledWith(versionAction('1.0.0'), SAVED_ID);
    expect(onShowSaved).not.toHaveBeenCalled();
  });

  it('given_injectsOnOlderVersions_should_replaceTheCardAndOpenItsVersions', () => {
    // Arrange
    versionsLoaded({
      '1.1.0': usagesOf(usage('inject-new')),
      '1.0.0': usagesOf(usage('inject-1'), usage('inject-2')),
      '0.9.0': usagesOf(),
    });
    const { afterSave, onUpdate, onShowSaved } = renderAfterSave();

    // Act
    act(() => afterSave(versionAction('1.1.0')));

    // Assert
    expect(onUpdate).toHaveBeenCalledWith(versionAction('1.1.0'), SAVED_ID);
    expect(onShowSaved).toHaveBeenCalledWith(versionAction('1.1.0'));
  });

  it('given_noInjectOnOlderVersions_should_replaceTheCardOnly', () => {
    // Arrange
    versionsLoaded({
      '1.1.0': usagesOf(),
      '1.0.0': usagesOf(),
    });
    const { afterSave, onUpdate, onShowSaved } = renderAfterSave();

    // Act
    act(() => afterSave(versionAction('1.1.0')));

    // Assert
    expect(onUpdate).toHaveBeenCalledWith(versionAction('1.1.0'), SAVED_ID);
    expect(onShowSaved).not.toHaveBeenCalled();
  });

  it('given_usagesStillLoading_should_wait', () => {
    // Arrange
    mocks.usePayloadVersions.mockReturnValue([payloadVersion('1.1.0'), payloadVersion('1.0.0')]);
    mocks.useUsagesByVersion.mockReturnValue(null);
    const { afterSave, onUpdate, onShowSaved } = renderAfterSave();

    // Act
    act(() => afterSave(versionAction('1.1.0')));

    // Assert
    expect(onUpdate).not.toHaveBeenCalled();
    expect(onShowSaved).not.toHaveBeenCalled();
  });
});
