import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  catalog: true,
  search: vi.fn(),
}));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock(import('../../../../../utils/utils'), async importOriginal => ({
  ...await importOriginal(),
  isFeatureEnabled: () => mocks.catalog,
}));

const { default: useNotInstalledStat } = await import('../../../../../components/common/marketplace/page/useNotInstalledStat');

describe('useNotInstalledStat', () => {
  beforeEach(() => {
    mocks.search.mockResolvedValue({ data: { totalElements: 7 } });
  });

  afterEach(() => {
    mocks.catalog = true;
    vi.clearAllMocks();
  });

  it('given_theCatalogue_should_countTheAvailableTabWithOneSmallSearch', async () => {
    // Act
    const { result } = renderHook(() => useNotInstalledStat(mocks.search, '/admin/scenarios/available'));

    // Assert
    await waitFor(() => expect(result.current[0]?.value).toBe(7));
    expect(result.current[0]).toEqual(expect.objectContaining({
      label: 'Not installed',
      to: '/admin/scenarios/available',
    }));
    expect(mocks.search).toHaveBeenCalledWith(expect.objectContaining({ size: 1 }));
  });

  it('given_noCatalogue_should_showNoTileAndAskNothing', () => {
    // Arrange
    mocks.catalog = false;

    // Act
    const { result } = renderHook(() => useNotInstalledStat(mocks.search, '/admin/scenarios/available'));

    // Assert
    expect(result.current).toEqual([]);
    expect(mocks.search).not.toHaveBeenCalled();
  });
});
