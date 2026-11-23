import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ catalogEnabled: true }));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock(import('../../../../../utils/utils'), async importOriginal => ({
  ...await importOriginal(),
  isFeatureEnabled: () => mocks.catalogEnabled,
}));

const { default: MarketplaceCatalogTabs } = await import('../../../../../components/common/marketplace/page/MarketplaceCatalogTabs');

const renderTabs = () => render(
  <MemoryRouter>
    <MarketplaceCatalogTabs active="available" baseUrl="/admin/scenarios" />
  </MemoryRouter>,
);

describe('MarketplaceCatalogTabs', () => {
  afterEach(cleanup);

  it('given_theCatalogue_should_linkBothTabsAndMarkTheActiveOne', () => {
    // Arrange
    mocks.catalogEnabled = true;

    // Act
    renderTabs();

    // Assert
    const active = screen.getByText('Active').closest('a');
    const available = screen.getByText('Available').closest('a');
    expect(active?.getAttribute('href')).toBe('/admin/scenarios');
    expect(available?.getAttribute('href')).toBe('/admin/scenarios/available');
    expect(available?.getAttribute('aria-current')).toBe('page');
    expect(active?.getAttribute('aria-current')).toBeNull();
  });

  it('given_noCatalogue_should_showNoTabs', () => {
    // Arrange
    mocks.catalogEnabled = false;

    // Act
    const { container } = renderTabs();

    // Assert
    expect(container.childElementCount).toBe(0);
  });
});
