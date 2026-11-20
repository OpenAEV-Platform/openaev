import { TooltipProvider } from '@filigran/design-system';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import MarketplaceToolbar from '../../../../../components/common/marketplace/toolbar/MarketplaceToolbar';
import { type SortHelpers } from '../../../../../components/common/queryable/sort/SortHelpers';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));

const sortHelpers: SortHelpers = {
  getSortBy: () => 'name',
  getSortAsc: () => true,
  handleDirectedSort: vi.fn(),
  handleSort: vi.fn(),
};

const renderToolbar = (props: Partial<Parameters<typeof MarketplaceToolbar>[0]> = {}) => render(
  <TooltipProvider>
    <MarketplaceToolbar keyword="" onSearch={vi.fn()} sortHelpers={sortHelpers} count={3} {...props} />
  </TooltipProvider>,
);

describe('MarketplaceToolbar', () => {
  afterEach(cleanup);

  it('given_sortOptions_should_showTheSortMenu', () => {
    // Act
    renderToolbar({
      sortOptions: [{
        field: 'name',
        label: 'Name',
      }],
    });

    // Assert
    expect(screen.getByRole('combobox')).toBeTruthy();
  });

  it('given_noSortOptions_should_showNoSortMenu', () => {
    // Act
    renderToolbar();

    // Assert
    expect(screen.queryByRole('combobox')).toBeNull();
  });

  it('given_aPlaceholderAndActions_should_showThemNextToTheCount', () => {
    // Act
    renderToolbar({
      placeholder: 'Search the catalog...',
      actions: <button type="button">Grid</button>,
    });

    // Assert
    expect(screen.getByPlaceholderText('Search the catalog...')).toBeTruthy();
    expect(screen.getByTestId('marketplace-results-count').parentElement?.textContent).toContain('Grid');
  });
});
