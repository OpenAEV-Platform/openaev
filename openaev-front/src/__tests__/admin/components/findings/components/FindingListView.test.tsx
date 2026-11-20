import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import FindingListView from '../../../../../admin/components/findings/components/FindingListView';
import { type TargetSimple } from '../../../../../utils/api-types';
import { aFinding, aQueryableHelpers, renderInApp } from '../findingTestUtils';

// -- MOCKS --

// Prints what the list hands to the chips, as `TYPE:name`.
vi.mock('../../../../../components/ItemTargets', async () => {
  const { createElement } = await import('react');
  return {
    default: ({ targets }: { targets: TargetSimple[] }) => createElement(
      'ul',
      null,
      targets.map(target => createElement('li', { key: target.target_id }, `${target.target_type}:${target.target_name}`)),
    ),
  };
});

// src/components holds an empty, tracked Empty.jsx that Vite resolves before Empty.tsx: stand in for the empty state.
vi.mock('../../../../../components/Empty', async () => {
  const { createElement } = await import('react');
  return { default: ({ message }: { message: string }) => createElement('p', null, message) };
});

// -- HELPERS --

const aFindingWithAssets = () => aFinding({
  finding_assets: [{
    asset_id: 'asset-1',
    asset_name: 'Host A',
    asset_category: 'HOST',
    endpoint_platform: 'Linux',
  }],
  finding_asset_groups: [{
    asset_group_id: 'group-1',
    asset_group_name: 'Group 1',
  }],
});

// A header cell is the parent of its label.
const widthOf = (label: string) => parseFloat(screen.getByText(label).parentElement!.style.width);

// -- TESTS --

describe('FindingListView', () => {
  afterEach(cleanup);

  it('should render one row per finding, linking to it, with its type, value, assets and dates', () => {
    // Arrange + Act
    renderInApp(<FindingListView loading={false} findings={[aFindingWithAssets()]} queryableHelpers={aQueryableHelpers()} />);

    // Assert
    const row = screen.getByTestId('finding-row');
    expect(within(row).getByRole('link')).toHaveAttribute('href', '/admin/findings/finding-1');
    expect(within(row).getByText('IPv4')).toBeInTheDocument();
    expect(within(row).getByText('10.0.0.1')).toBeInTheDocument();
    expect(within(row).getByText('ASSETS:Host A')).toBeInTheDocument();
    expect(within(row).getAllByText(/2026/)).toHaveLength(2);
  });

  it('should not show the asset groups of a finding', () => {
    // Arrange + Act
    renderInApp(<FindingListView loading={false} findings={[aFindingWithAssets()]} queryableHelpers={aQueryableHelpers()} />);

    // Assert
    expect(screen.queryByText('Asset groups')).toBeNull();
    expect(screen.queryByText('ASSETS_GROUPS:Group 1')).toBeNull();
  });

  it('should fall back to the raw type of a finding whose type has no label', () => {
    // Arrange + Act
    renderInApp(<FindingListView loading={false} findings={[aFinding({ finding_type: 'brand_new' as never })]} queryableHelpers={aQueryableHelpers()} />);

    // Assert
    expect(within(screen.getByTestId('finding-row')).getByText('brand_new')).toBeInTheDocument();
  });

  describe('headers', () => {
    it('should sort by a sortable header, and not by the assets one', () => {
      // Arrange
      const queryableHelpers = aQueryableHelpers();
      renderInApp(<FindingListView loading={false} findings={[]} queryableHelpers={queryableHelpers} />);

      // Act
      fireEvent.click(screen.getByText('Assets'));
      fireEvent.click(screen.getByText('Value'));

      // Assert
      expect(queryableHelpers.sortHelpers.handleSort).toHaveBeenCalledTimes(1);
      expect(queryableHelpers.sortHelpers.handleSort).toHaveBeenCalledWith('finding_value');
    });

    it('should hide the given columns and stretch the others to fill the row', () => {
      // Arrange + Act
      renderInApp(
        <FindingListView
          loading={false}
          findings={[]}
          queryableHelpers={aQueryableHelpers()}
          hiddenFields={['finding_created_at', 'finding_updated_at']}
        />,
      );

      // Assert
      expect(screen.queryByText('First seen')).toBeNull();
      expect(screen.queryByText('Last seen')).toBeNull();
      expect(widthOf('Type') + widthOf('Value') + widthOf('Assets')).toBeCloseTo(100, 0);
    });

    it('should fill the row when no column is hidden', () => {
      // Arrange + Act
      renderInApp(<FindingListView loading={false} findings={[]} queryableHelpers={aQueryableHelpers()} />);

      // Assert
      const widths = ['Type', 'Value', 'Assets', 'First seen', 'Last seen'].map(widthOf);
      expect(widths.reduce((sum, width) => sum + width, 0)).toBeCloseTo(100, 0);
    });
  });

  describe('states', () => {
    it('should render neither rows nor the empty message while loading', () => {
      // Arrange + Act
      renderInApp(<FindingListView loading findings={[]} queryableHelpers={aQueryableHelpers()} />);

      // Assert
      expect(screen.queryAllByTestId('finding-row')).toHaveLength(0);
      expect(screen.queryByText('No finding found.')).toBeNull();
    });

    it('should say so when there is no finding', () => {
      // Arrange + Act
      renderInApp(<FindingListView loading={false} findings={[]} queryableHelpers={aQueryableHelpers()} />);

      // Assert
      expect(screen.getByText('No finding found.')).toBeInTheDocument();
    });
  });
});
