import '@testing-library/jest-dom/vitest';

import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import AssetGroupsList from '../../../../../admin/components/assets/asset_groups/AssetGroupsList';
import { type AssetGroupOutput } from '../../../../../utils/api-types';
import { EndpointContext } from '../../../../../utils/context/endpoint/EndpointContext';

let storeAssetGroups: Record<string, AssetGroupOutput> = {};

vi.mock('../../../../../store', () => ({ useHelper: vi.fn((selector: (helper: { getAssetGroupMaps: () => unknown }) => unknown) => selector({ getAssetGroupMaps: () => storeAssetGroups })) }));

vi.mock('react-router', () => ({ useLocation: () => ({ pathname: '/admin/simulations/simulation-id/injects' }) }));

vi.mock('../../../../../actions/asset_groups/assetgroup-action', () => ({ findAssetGroups: vi.fn() }));

vi.mock('../../../../../components/ItemTags', () => ({ default: ({ tags }: { tags?: string[] }) => <span data-testid="asset-group-tags">{(tags ?? []).join(',')}</span> }));

vi.mock('../../../../../components/PaginatedListLoader', () => ({ default: () => <div data-testid="loader" /> }));

const assetGroup = (tags: string[]): AssetGroupOutput => ({
  asset_group_id: 'asset-group-id',
  asset_group_name: 'Asset group',
  asset_group_tags: tags,
});

const fetchAssetGroupsByIds = vi.fn();

// Stable across renders, like the inject form's asset group ids: a new array on each render would
// re-run the list's effect and hide a list that does not follow the store.
const ASSET_GROUP_IDS = ['asset-group-id'];

const renderList = (): ReactElement => (
  <ThemeProvider theme={createTheme()}>
    <EndpointContext.Provider value={{
      fetchEndpointsByIds: vi.fn(),
      fetchAssetGroupsByIds,
    }}
    >
      <AssetGroupsList assetGroupIds={ASSET_GROUP_IDS} renderActions={() => <span />} />
    </EndpointContext.Provider>
  </ThemeProvider>
);

describe('AssetGroupsList', () => {
  beforeEach(() => {
    storeAssetGroups = {};
    fetchAssetGroupsByIds.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it('shows an asset group fetched because the store did not hold it', async () => {
    // Arrange
    fetchAssetGroupsByIds.mockResolvedValue({ data: [assetGroup(['tag-1'])] });

    // Act
    render(renderList());

    // Assert
    expect(await screen.findByTestId('asset-group-tags')).toHaveTextContent('tag-1');
    expect(fetchAssetGroupsByIds).toHaveBeenCalledWith(['asset-group-id']);
  });

  it('shows the edited asset group once the store holds it, without fetching it again', async () => {
    // Arrange
    fetchAssetGroupsByIds.mockResolvedValue({ data: [assetGroup(['tag-1'])] });
    const { rerender } = render(renderList());
    expect(await screen.findByTestId('asset-group-tags')).toHaveTextContent('tag-1');

    // Act: the popover update writes the edited asset group to the store
    storeAssetGroups = { 'asset-group-id': assetGroup(['tag-1', 'tag-2']) };
    rerender(renderList());

    // Assert
    expect(screen.getByTestId('asset-group-tags')).toHaveTextContent('tag-1,tag-2');
    expect(fetchAssetGroupsByIds).toHaveBeenCalledTimes(1);
  });

  it('shows an asset group already held by the store without fetching it', () => {
    // Arrange
    storeAssetGroups = { 'asset-group-id': assetGroup(['tag-1']) };

    // Act
    render(renderList());

    // Assert
    expect(screen.getByTestId('asset-group-tags')).toHaveTextContent('tag-1');
    expect(fetchAssetGroupsByIds).not.toHaveBeenCalled();
  });
});
