import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type CatalogScenarioOutput, type SearchPaginationInput } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';

const mocks = vi.hoisted(() => ({
  searchCatalogScenarios: vi.fn(),
  fetchCatalogScenarioFacetCounts: vi.fn(),
  loadMore: { current: () => {} },
}));

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [name, value]) => text.replace(`{${name}}`, String(value)), key),
    nsd: (date: string) => date,
  }),
}));
vi.mock('../../../../../actions/catalog/scenario-catalog-actions', () => ({
  searchCatalogScenarios: mocks.searchCatalogScenarios,
  fetchCatalogScenarioFacetCounts: mocks.fetchCatalogScenarioFacetCounts,
}));
vi.mock('../../../../../components/common/queryable/pagination/PaginationComponentV2', async () => ({ default: (await import('../../../../utils/FakePaginationComponentV2')).default }));
vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));
vi.mock(import('../../../../../utils/utils'), async importOriginal => ({
  ...await importOriginal(),
  isFeatureEnabled: () => true,
}));
vi.mock('../../../../../admin/components/scenarios/page/ScenariosHero', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/scenarios/catalog/ScenarioCatalogDrawer', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/scenarios/catalog/CatalogScenarioInstallDrawer', () => ({
  default: ({ scenario, onInstalled }: {
    scenario: CatalogScenarioOutput;
    onInstalled: (scenario: CatalogScenarioOutput) => void;
  }) => <button type="button" data-testid="confirm-install" onClick={() => onInstalled(scenario)} />,
}));

const { default: ScenariosAvailable } = await import('../../../../../admin/components/scenarios/catalog/ScenariosAvailable');

const scenario = (name: string, type: string): CatalogScenarioOutput => ({
  catalog_scenario_external_id: name.toLowerCase(),
  catalog_scenario_name: name,
  catalog_scenario_type: type,
  catalog_scenario_version: '1.0',
  catalog_scenario_platforms: ['Linux'],
  catalog_scenario_missing_requirements: [],
});
const PHISHING = scenario('Phishing', 'TIME_BASED');
const RANSOMWARE = scenario('Ransomware', 'CHAINED');

const page = (content: CatalogScenarioOutput[], totalElements = content.length) => ({
  data: {
    content,
    totalElements,
  },
});

const renderPage = () => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <MemoryRouter>
        <ScenariosAvailable />
      </MemoryRouter>
    </TooltipProvider>
  </ThemeProvider>,
);

const lastSearch = (): SearchPaginationInput => mocks.searchCatalogScenarios.mock.lastCall?.[0];
const cardTitles = () => screen.queryAllByTestId('scenario-catalog-card').map(card => card.textContent);

describe('ScenariosAvailable', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.spyOn(MESSAGING$, 'notifySuccess').mockImplementation(() => undefined);
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: (entries: { isIntersecting: boolean }[]) => void) {
        mocks.loadMore.current = () => callback([{ isIntersecting: true }]);
      }

      observe() {}

      disconnect() {}
    });
    mocks.searchCatalogScenarios.mockResolvedValue(page([PHISHING, RANSOMWARE]));
    mocks.fetchCatalogScenarioFacetCounts.mockResolvedValue({
      catalog_item_scenario_type: {
        TIME_BASED: 1,
        CHAINED: 1,
      },
    });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('given_catalogueScenarios_should_showOneCardEachNewestFirst', async () => {
    // Act
    renderPage();

    // Assert
    await waitFor(() => expect(cardTitles()).toHaveLength(2));
    expect(lastSearch().sorts).toEqual([{
      property: 'catalog_item_published_at',
      direction: 'DESC',
    }]);
    expect(screen.getByText('Available').closest('a')?.getAttribute('href')).toBe('/admin/scenarios/available');
  });

  it('given_aSearchText_should_searchTheServerWithIt', async () => {
    // Arrange
    renderPage();
    await waitFor(() => expect(cardTitles()).toHaveLength(2));

    // Act
    fireEvent.change(screen.getByLabelText('Search'), { target: { value: 'ransom' } });

    // Assert
    await waitFor(() => expect(lastSearch().textSearch).toBe('ransom'));
  });

  it('given_aTypeFacet_should_filterTheSearchAndShowItsChip', async () => {
    // Arrange
    renderPage();
    await waitFor(() => expect(cardTitles()).toHaveLength(2));

    // Act
    fireEvent.click(screen.getByRole('checkbox', { name: 'Chained' }));

    // Assert
    await waitFor(() => expect(screen.getByTestId('filter-chip').textContent).toBe('catalog_item_scenario_type = CHAINED'));
    expect(lastSearch().filterGroup?.filters).toEqual([expect.objectContaining({
      key: 'catalog_item_scenario_type',
      values: ['CHAINED'],
    })]);
  });

  it('given_moreScenariosThanThePage_should_loadTheNextPageAtTheBottom', async () => {
    // Arrange
    mocks.searchCatalogScenarios
      .mockResolvedValueOnce(page([PHISHING], 2))
      .mockResolvedValueOnce(page([RANSOMWARE], 2));
    renderPage();
    await waitFor(() => expect(cardTitles()).toHaveLength(1));

    // Act
    mocks.loadMore.current();

    // Assert
    await waitFor(() => expect(cardTitles()).toHaveLength(2));
    expect(lastSearch().page).toBe(1);
  });

  it('given_theListView_should_showOneRowPerScenario', async () => {
    // Arrange
    renderPage();
    await waitFor(() => expect(cardTitles()).toHaveLength(2));

    // Act
    fireEvent.click(screen.getByRole('radio', { name: 'List view' }));

    // Assert
    expect(cardTitles()).toHaveLength(0);
    expect(screen.getByRole('button', { name: /Ransomware/ })).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'Install' })).toHaveLength(2);
  });

  it('given_noMatchingScenario_should_offerToResetTheFilters', async () => {
    // Arrange
    mocks.searchCatalogScenarios.mockResolvedValue(page([]));
    renderPage();
    fireEvent.change(screen.getByLabelText('Search'), { target: { value: 'nothing like it' } });
    await waitFor(() => expect(screen.getByText('No scenario matches these filters.')).toBeTruthy());

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Reset filters' }));

    // Assert
    await waitFor(() => expect(lastSearch().textSearch ?? '').toBe(''));
  });

  it('given_anInstalledScenario_should_notifyAndSearchAgain', async () => {
    // Arrange
    renderPage();
    await waitFor(() => expect(cardTitles()).toHaveLength(2));
    const searches = mocks.searchCatalogScenarios.mock.calls.length;

    // Act
    fireEvent.click(screen.getAllByRole('button', { name: 'Install' })[0]);
    fireEvent.click(screen.getByTestId('confirm-install'));

    // Assert
    expect(MESSAGING$.notifySuccess).toHaveBeenCalledWith('Phishing v1.0 installed');
    await waitFor(() => expect(mocks.searchCatalogScenarios.mock.calls.length).toBeGreaterThan(searches));
  });
});
