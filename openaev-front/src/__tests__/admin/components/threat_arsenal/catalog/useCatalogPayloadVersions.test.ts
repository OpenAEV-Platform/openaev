import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useCatalogPayloadVersions from '../../../../../admin/components/threat_arsenal/catalog/useCatalogPayloadVersions';

const { fetchVersions } = vi.hoisted(() => ({ fetchVersions: vi.fn() }));

vi.mock('../../../../../actions/catalog/payload-catalog-actions', () => ({ fetchCatalogPayloadVersions: fetchVersions }));

describe('useCatalogPayloadVersions', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('given no payload should fetch nothing', () => {
    // Act
    const { result } = renderHook(() => useCatalogPayloadVersions(undefined));

    // Assert
    expect(result.current).toEqual([]);
    expect(fetchVersions).not.toHaveBeenCalled();
  });

  it('given a catalogue payload should load its offered versions', async () => {
    // Arrange
    const versions = [{ catalog_payload_version: '2' }];
    fetchVersions.mockResolvedValue(versions);

    // Act
    const { result } = renderHook(() => useCatalogPayloadVersions('mimikatz'));

    // Assert
    await waitFor(() => expect(result.current).toEqual(versions));
    expect(fetchVersions).toHaveBeenCalledWith('mimikatz');
  });
});
