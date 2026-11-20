import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type AvailableActionOutput, type SearchPaginationInput } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';

const mocks = vi.hoisted(() => ({
  searchAvailableActions: vi.fn(),
  fetchAvailableActionFacetCounts: vi.fn(),
  fetchCatalogSettings: vi.fn(),
  installAllCatalogPayloads: vi.fn(),
  installCatalogPayload: vi.fn(),
  updateCatalogSettings: vi.fn(),
  loadMore: { current: () => {} },
}));

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [name, value]) => text.replace(`{${name}}`, String(value)), key),
    nsd: (date: string) => date,
    tPick: (labels: Record<string, string>) => labels.en,
  }),
}));
vi.mock('../../../../../actions/catalog/payload-catalog-actions', () => mocks);
vi.mock('../../../../../components/common/queryable/pagination/PaginationComponentV2', async () => ({ default: (await import('../../../../utils/FakePaginationComponentV2')).default }));
vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));
vi.mock(import('../../../../../utils/utils'), async importOriginal => ({
  ...await importOriginal(),
  isFeatureEnabled: () => true,
}));
vi.mock('../../../../../admin/components/threat_arsenal/page/ThreatArsenalHero', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/threat_arsenal/catalog/ThreatArsenalCatalogDrawer', () => ({ default: () => null }));

const { default: ThreatArsenalAvailable } = await import('../../../../../admin/components/threat_arsenal/catalog/ThreatArsenalAvailable');

const payload = (id: string, name: string, domain?: string): AvailableActionOutput => ({
  action_kind: 'PAYLOAD',
  action_id: id,
  action_labels: { en: name },
  action_domain: domain,
  action_payload_version: '2.0',
  action_platforms: ['Windows'],
});
const ENDPOINT_SCAN = payload('scan', 'Scan', 'Endpoint');
const NO_DOMAIN_DUMP = payload('dump', 'Dump');
const NETWORK_PING: AvailableActionOutput = {
  action_kind: 'INTEGRATION',
  action_id: 'ping',
  action_labels: { en: 'Ping' },
  action_domain: 'Network',
  action_integration_id: 'nmap',
  action_integration_title: 'Nmap',
  action_attack_patterns: [],
  action_platforms: ['Linux'],
};

const page = (content: AvailableActionOutput[], totalElements = content.length) => ({
  data: {
    content,
    totalElements,
  },
});

const renderPage = () => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <MemoryRouter>
        <ThreatArsenalAvailable />
      </MemoryRouter>
    </TooltipProvider>
  </ThemeProvider>,
);

const lastSearch = (): SearchPaginationInput => mocks.searchAvailableActions.mock.lastCall?.[0];
const sectionTitles = () => Array.from(document.querySelectorAll('section > header h2')).map(heading => heading.textContent);

describe('ThreatArsenalAvailable', () => {
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
    mocks.searchAvailableActions.mockResolvedValue(page([NO_DOMAIN_DUMP, NETWORK_PING, ENDPOINT_SCAN]));
    mocks.fetchAvailableActionFacetCounts.mockResolvedValue({
      catalog_item_domain: {
        Endpoint: 1,
        Network: 1,
      },
      catalog_item_platforms: {
        Windows: 2,
        Linux: 1,
        MacOS: 0,
      },
    });
    mocks.fetchCatalogSettings.mockResolvedValue({
      payload_auto_install: false,
      scenario_auto_install: true,
    });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('given_actionsOfSeveralDomains_should_showOneSectionPerDomainWithoutDomainLast', async () => {
    // Act
    renderPage();

    // Assert
    await waitFor(() => expect(screen.getAllByTestId('threat-arsenal-catalog-card')).toHaveLength(2));
    expect(screen.getAllByTestId('threat-arsenal-contract-catalog-card')).toHaveLength(1);
    expect(sectionTitles()).toEqual(['Endpoint', 'Network', 'Other']);
  });

  it('given_aSearchText_should_searchTheServerWithIt', async () => {
    // Arrange
    renderPage();
    await waitFor(() => expect(mocks.searchAvailableActions).toHaveBeenCalled());

    // Act
    fireEvent.change(screen.getByLabelText('Search'), { target: { value: 'scan' } });

    // Assert
    await waitFor(() => expect(lastSearch().textSearch).toBe('scan'));
  });

  it('given_aPlatformFacet_should_filterTheSearchAndShowItsChip', async () => {
    // Arrange
    renderPage();
    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Linux' })).toBeTruthy());

    // Act
    fireEvent.click(screen.getByRole('checkbox', { name: 'Linux' }));

    // Assert
    await waitFor(() => expect(screen.getByTestId('filter-chip').textContent).toBe('catalog_item_platforms = Linux'));
    expect(lastSearch().filterGroup?.filters).toEqual([expect.objectContaining({
      key: 'catalog_item_platforms',
      values: ['Linux'],
    })]);
  });

  it('given_moreActionsThanThePage_should_loadTheNextPageAtTheBottom', async () => {
    // Arrange
    mocks.searchAvailableActions
      .mockResolvedValueOnce(page([ENDPOINT_SCAN, NO_DOMAIN_DUMP], 3))
      .mockResolvedValueOnce(page([NETWORK_PING], 3));
    renderPage();
    await waitFor(() => expect(screen.getAllByTestId('threat-arsenal-catalog-card')).toHaveLength(2));

    // Act
    mocks.loadMore.current();

    // Assert
    await waitFor(() => expect(screen.getAllByTestId('threat-arsenal-contract-catalog-card')).toHaveLength(1));
    expect(lastSearch().page).toBe(1);
  });

  it('given_theListView_should_showOneRowPerAction', async () => {
    // Arrange
    renderPage();
    await waitFor(() => expect(screen.getAllByTestId('threat-arsenal-catalog-card')).toHaveLength(2));

    // Act
    fireEvent.click(screen.getByRole('radio', { name: 'List view' }));

    // Assert
    expect(screen.queryAllByTestId('threat-arsenal-catalog-card')).toHaveLength(0);
    expect(screen.getByRole('link', { name: /Ping/ }).getAttribute('href')).toBe('/admin/integrations/catalog/nmap');
    expect(screen.getAllByRole('button', { name: 'Install' })).toHaveLength(2);
  });

  it('given_anInstalledPayload_should_markItInstalledAndNotify', async () => {
    // Arrange
    mocks.installCatalogPayload.mockResolvedValue({});
    renderPage();
    await waitFor(() => expect(screen.getAllByTestId('threat-arsenal-catalog-card')).toHaveLength(2));
    const scanCard = screen.getAllByTestId('threat-arsenal-catalog-card')[0];

    // Act
    fireEvent.click(within(scanCard).getByRole('button', { name: 'Install' }));

    // Assert
    await waitFor(() => expect(within(scanCard).getByText('Installed')).toBeTruthy());
    expect(mocks.installCatalogPayload).toHaveBeenCalledWith('scan', '2.0');
    expect(MESSAGING$.notifySuccess).toHaveBeenCalledWith('Scan v2.0 installed');
  });

  it('given_installAll_should_notifyAndSearchAgain', async () => {
    // Arrange
    mocks.installAllCatalogPayloads.mockResolvedValue({ installed_ids: ['scan', 'dump'] });
    renderPage();
    await waitFor(() => expect(mocks.searchAvailableActions).toHaveBeenCalledTimes(1));

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Install all' }));

    // Assert
    await waitFor(() => expect(mocks.searchAvailableActions).toHaveBeenCalledTimes(2));
    expect(MESSAGING$.notifySuccess).toHaveBeenCalledWith('2 actions installed');
  });

  it('given_theAutoInstallSwitch_should_saveItAndKeepTheScenarioSetting', async () => {
    // Arrange
    mocks.updateCatalogSettings.mockResolvedValue({
      payload_auto_install: true,
      scenario_auto_install: true,
    });
    renderPage();
    const toggle = await screen.findByRole('switch');

    // Act
    fireEvent.click(toggle);

    // Assert
    await waitFor(() => expect(mocks.updateCatalogSettings).toHaveBeenCalledWith({
      payload_auto_install: true,
      scenario_auto_install: true,
    }));
  });
});
