import { cleanup, renderHook, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { UserContext, type UserContextType } from '../../../utils/hooks/useAuth';
import useXtmHubDownloadDocument from '../../../utils/hooks/useXtmHubDownloadDocument';
import XtmHubClient from '../../../utils/xtm-hub-client';

vi.mock('../../../store', () => ({ useHelper: () => ({ tenant_xtmhub_registration_status: 'REGISTERED' }) }));
vi.mock('../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));
vi.mock('../../../utils/hooks/useXtmHubUserPlatformToken', () => ({ default: () => ({ userPlatformToken: 'hub-token' }) }));

const file = new File([], 'downloaded.zip');

const renderDownload = (onSuccess: (file: File) => void) => {
  let settings = { xtm_hub_url: 'https://hub.filigran.io' };
  const wrapper = ({ children }: { children: ReactNode }) => (
    <UserContext.Provider value={{ settings } as unknown as UserContextType}>
      {children}
    </UserContext.Provider>
  );
  const { rerender } = renderHook(() => useXtmHubDownloadDocument({
    serviceInstanceId: 'service-1',
    fileId: 'file-1',
    onSuccess,
    onError: vi.fn(),
  }), {
    wrapper,
    reactStrictMode: true,
  });
  // A new settings object re-runs the download effect.
  const changeSettings = () => {
    settings = { ...settings };
    rerender();
  };
  return { changeSettings };
};

describe('useXtmHubDownloadDocument', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('hands the document over once when StrictMode runs the effect twice', async () => {
    // Arrange
    vi.spyOn(XtmHubClient, 'fetchDocument').mockResolvedValue(file);
    const onSuccess = vi.fn();

    // Act
    renderDownload(onSuccess);

    // Assert
    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
    expect(onSuccess).toHaveBeenCalledExactlyOnceWith(file);
  });

  it('does not hand the document over again when a dependency changes after the download', async () => {
    // Arrange
    const fetchDocument = vi.spyOn(XtmHubClient, 'fetchDocument').mockResolvedValue(file);
    const onSuccess = vi.fn();
    const { changeSettings } = renderDownload(onSuccess);
    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
    const fetchCount = fetchDocument.mock.calls.length;

    // Act
    changeSettings();

    // Assert
    await Promise.resolve();
    expect(fetchDocument).toHaveBeenCalledTimes(fetchCount);
    expect(onSuccess).toHaveBeenCalledOnce();
  });
});
