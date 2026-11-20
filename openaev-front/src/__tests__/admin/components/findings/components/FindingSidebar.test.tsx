import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import FindingSidebar from '../../../../../admin/components/findings/components/FindingSidebar';
import { type FilterHelpers } from '../../../../../components/common/queryable/filter/FilterHelpers';
import { type Filter, type Option, type SearchPaginationInput } from '../../../../../utils/api-types';
import { renderInApp } from '../findingTestUtils';

// -- MOCKS --

const mockSearchAssets = vi.fn<() => Promise<{ data: Option[] }>>();

vi.mock('../../../../../actions/assets/endpoint-actions', () => ({ searchEndpointLinkedToFindingsAsOption: () => mockSearchAssets() }));

// -- HELPERS --

const ASSETS: Option[] = [
  {
    id: 'asset-a',
    label: 'Host A',
  },
  {
    id: 'asset-b',
    label: 'Host B',
  },
];

const aFilter = (key: string, values: string[]): Filter => ({
  id: `filter-${key}`,
  key,
  operator: 'eq',
  mode: 'and',
  values,
});

const renderSidebar = (filters: Filter[] = []) => {
  const filterHelpers = {
    handleAddFilterWithEmptyValue: vi.fn(),
    handleRemoveFilterById: vi.fn(),
    handleRemoveFilterByKey: vi.fn(),
    handleUpdateValuesById: vi.fn(),
  };
  const searchPaginationInput = {
    filterGroup: {
      mode: 'and',
      filters,
    },
  } as SearchPaginationInput;
  renderInApp(<FindingSidebar searchPaginationInput={searchPaginationInput} filterHelpers={filterHelpers as unknown as FilterHelpers} />);
  return filterHelpers;
};

const checkbox = (name: string) => screen.findByRole('checkbox', { name });

// -- TESTS --

describe('FindingSidebar', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('type facet', () => {
    it('should add a type filter when a type is checked for the first time', async () => {
      // Arrange
      mockSearchAssets.mockResolvedValue({ data: ASSETS });
      const helpers = renderSidebar();

      // Act
      fireEvent.click(await checkbox('IPv4'));

      // Assert
      expect(helpers.handleAddFilterWithEmptyValue).toHaveBeenCalledWith(expect.objectContaining({
        key: 'finding_type',
        values: ['ipv4'],
      }));
    });

    it('should check the types of the current filter and drop the filter when the last one is unchecked', async () => {
      // Arrange
      mockSearchAssets.mockResolvedValue({ data: ASSETS });
      const helpers = renderSidebar([aFilter('finding_type', ['ipv4'])]);
      const ipv4 = await checkbox('IPv4');

      // Act
      fireEvent.click(ipv4);

      // Assert
      expect(ipv4).toHaveAttribute('aria-checked', 'true');
      expect(helpers.handleRemoveFilterById).toHaveBeenCalledWith('filter-finding_type');
    });
  });

  describe('asset facet', () => {
    it('should check the assets of a restored filter once their options have loaded', async () => {
      // Arrange + Act: the filter comes from the local storage, the options from the network
      mockSearchAssets.mockResolvedValue({ data: ASSETS });
      renderSidebar([aFilter('finding_assets', ['asset-a'])]);

      // Assert
      await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Host A' })).toHaveAttribute('aria-checked', 'true'));
      expect(screen.getByRole('checkbox', { name: 'Host B' })).toHaveAttribute('aria-checked', 'false');
    });

    it('should add an asset to the filter without dropping the ones already selected', async () => {
      // Arrange
      mockSearchAssets.mockResolvedValue({ data: ASSETS });
      const helpers = renderSidebar([aFilter('finding_assets', ['asset-a'])]);
      await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Host A' })).toHaveAttribute('aria-checked', 'true'));

      // Act
      fireEvent.click(screen.getByRole('checkbox', { name: 'Host B' }));

      // Assert
      expect(helpers.handleUpdateValuesById).toHaveBeenCalledWith('filter-finding_assets', ['asset-a', 'asset-b']);
    });

    it('should drop the filter when its only asset is unchecked', async () => {
      // Arrange
      mockSearchAssets.mockResolvedValue({ data: ASSETS });
      const helpers = renderSidebar([aFilter('finding_assets', ['asset-a'])]);
      await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Host A' })).toHaveAttribute('aria-checked', 'true'));

      // Act
      fireEvent.click(screen.getByRole('checkbox', { name: 'Host A' }));

      // Assert
      expect(helpers.handleRemoveFilterById).toHaveBeenCalledWith('filter-finding_assets');
    });

    it('should select only the clicked asset when several assets share the same name', async () => {
      // Arrange
      mockSearchAssets.mockResolvedValue({
        data: [
          {
            id: 'asset-1',
            label: 'localhost',
          },
          {
            id: 'asset-2',
            label: 'localhost',
          },
        ],
      });
      const helpers = renderSidebar([aFilter('finding_assets', ['asset-1'])]);
      await waitFor(() => expect(screen.getAllByRole('checkbox', { name: 'localhost' })).toHaveLength(2));

      // Act
      const [first, second] = screen.getAllByRole('checkbox', { name: 'localhost' });
      fireEvent.click(second);

      // Assert
      expect(first).toHaveAttribute('aria-checked', 'true');
      expect(second).toHaveAttribute('aria-checked', 'false');
      expect(helpers.handleUpdateValuesById).toHaveBeenCalledWith('filter-finding_assets', ['asset-1', 'asset-2']);
    });

    it('should keep a selected asset missing from the loaded options when another one is toggled', async () => {
      // Arrange: the restored filter holds an id the (possibly truncated) option list does not return
      mockSearchAssets.mockResolvedValue({ data: ASSETS });
      const helpers = renderSidebar([aFilter('finding_assets', ['asset-unlisted'])]);

      // Act
      fireEvent.click(await checkbox('Host B'));

      // Assert
      expect(helpers.handleUpdateValuesById).toHaveBeenCalledWith('filter-finding_assets', ['asset-unlisted', 'asset-b']);
    });

    it('should list no asset when their options cannot be loaded', async () => {
      // Arrange
      mockSearchAssets.mockRejectedValue(new Error('403'));

      // Act
      renderSidebar();

      // Assert
      await waitFor(() => expect(mockSearchAssets).toHaveBeenCalled());
      expect(await checkbox('IPv4')).toBeInTheDocument();
      expect(screen.queryByRole('checkbox', { name: 'Host A' })).toBeNull();
    });
  });
});
