import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import useNewVersionNotice from '../../../../../admin/components/threat_arsenal/versions/useNewVersionNotice';
import useSkipNewVersionDialog from '../../../../../admin/components/threat_arsenal/versions/useSkipNewVersionDialog';
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

const renderNotice = () => {
  const onUpdate = vi.fn();
  const { result } = renderHook(() => useNewVersionNotice(SAVED_ID, onUpdate));
  return {
    result,
    onUpdate,
  };
};

describe('useNewVersionNotice', () => {
  beforeEach(() => {
    localStorage.clear();
    mocks.usePayloadVersions.mockReset();
    mocks.useUsagesByVersion.mockReset();
  });

  it('given_saveInPlace_should_updateTheCardRightAway', () => {
    // Arrange
    versionsLoaded({});
    const { result, onUpdate } = renderNotice();

    // Act
    act(() => result.current.afterSave(versionAction('1.0.0')));

    // Assert
    expect(onUpdate).toHaveBeenCalledWith(versionAction('1.0.0'), SAVED_ID);
    expect(result.current.notice).toBeNull();
  });

  it('given_injectsOnOlderVersions_should_offerToMoveThem', () => {
    // Arrange
    versionsLoaded({
      '1.1.0': usagesOf(usage('inject-new')),
      '1.0.0': usagesOf(usage('inject-1'), usage('inject-2')),
      '0.9.0': usagesOf(),
    });
    const { result, onUpdate } = renderNotice();

    // Act
    act(() => result.current.afterSave(versionAction('1.1.0')));

    // Assert
    expect(onUpdate).not.toHaveBeenCalled();
    expect(result.current.notice?.action).toEqual(versionAction('1.1.0'));
    expect(result.current.notice?.olderVersions.map(older => older.version.payload_version)).toEqual(['1.0.0']);
  });

  it('given_noticeClosed_should_replaceTheCard', () => {
    // Arrange
    versionsLoaded({
      '1.1.0': usagesOf(),
      '1.0.0': usagesOf(usage('inject-1')),
    });
    const { result, onUpdate } = renderNotice();
    act(() => result.current.afterSave(versionAction('1.1.0')));

    // Act
    act(() => result.current.closeNotice());

    // Assert
    expect(onUpdate).toHaveBeenCalledWith(versionAction('1.1.0'), SAVED_ID);
    expect(result.current.notice).toBeNull();
  });

  it('given_noInjectOnOlderVersions_should_replaceTheCardWithoutNotice', () => {
    // Arrange
    versionsLoaded({
      '1.1.0': usagesOf(),
      '1.0.0': usagesOf(),
    });
    const { result, onUpdate } = renderNotice();

    // Act
    act(() => result.current.afterSave(versionAction('1.1.0')));

    // Assert
    expect(onUpdate).toHaveBeenCalledWith(versionAction('1.1.0'), SAVED_ID);
    expect(result.current.notice).toBeNull();
  });

  it('given_usagesStillLoading_should_waitWithoutNotice', () => {
    // Arrange
    mocks.usePayloadVersions.mockReturnValue([payloadVersion('1.1.0'), payloadVersion('1.0.0')]);
    mocks.useUsagesByVersion.mockReturnValue(null);
    const { result, onUpdate } = renderNotice();

    // Act
    act(() => result.current.afterSave(versionAction('1.1.0')));

    // Assert
    expect(onUpdate).not.toHaveBeenCalled();
    expect(result.current.notice).toBeNull();
  });

  it('given_dialogSwitchedOff_should_updateTheCardRightAway', () => {
    // Arrange
    versionsLoaded({
      '1.1.0': usagesOf(),
      '1.0.0': usagesOf(usage('inject-1')),
    });
    const skip = renderHook(() => useSkipNewVersionDialog());
    act(() => skip.result.current[1](true));
    const { result, onUpdate } = renderNotice();

    // Act
    act(() => result.current.afterSave(versionAction('1.1.0')));

    // Assert
    expect(onUpdate).toHaveBeenCalledWith(versionAction('1.1.0'), SAVED_ID);
    expect(result.current.notice).toBeNull();
  });
});
