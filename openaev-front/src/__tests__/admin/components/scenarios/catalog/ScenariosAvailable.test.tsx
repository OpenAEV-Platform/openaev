import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type CatalogScenarioOutput } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';

const mocks = vi.hoisted(() => ({ fetchCatalogScenarios: vi.fn() }));

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [name, value]) => text.replace(`{${name}}`, String(value)), key),
    nsd: (date: string) => date,
  }),
}));
vi.mock('../../../../../actions/catalog/scenario-catalog-actions', () => ({ fetchCatalogScenarios: mocks.fetchCatalogScenarios }));
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

const renderPage = () => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <MemoryRouter>
        <ScenariosAvailable />
      </MemoryRouter>
    </TooltipProvider>
  </ThemeProvider>,
);

const cardTitles = () => screen.queryAllByTestId('scenario-catalog-card').map(card => card.textContent);

describe('ScenariosAvailable', () => {
  beforeEach(() => {
    vi.spyOn(MESSAGING$, 'notifySuccess').mockImplementation(() => undefined);
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('given_catalogueScenarios_should_showOneCardEachSortedByName', async () => {
    // Arrange
    mocks.fetchCatalogScenarios.mockResolvedValue([RANSOMWARE, PHISHING]);

    // Act
    renderPage();

    // Assert
    await waitFor(() => expect(cardTitles()).toHaveLength(2));
    expect(cardTitles()[0]).toContain('Phishing');
    expect(screen.getByTestId('marketplace-results-count').textContent).toBe('2 results');
    expect(screen.getByText('Available').closest('a')?.getAttribute('href')).toBe('/admin/scenarios/available');
  });

  it('given_aTypeFacet_should_showOnlyTheScenariosOfThatType', async () => {
    // Arrange
    mocks.fetchCatalogScenarios.mockResolvedValue([RANSOMWARE, PHISHING]);
    renderPage();
    await waitFor(() => expect(cardTitles()).toHaveLength(2));

    // Act
    fireEvent.click(screen.getByRole('checkbox', { name: 'Chained' }));

    // Assert
    expect(cardTitles()).toHaveLength(1);
    expect(cardTitles()[0]).toContain('Ransomware');
  });

  it('given_aSearchMatchingNothing_should_offerToResetTheFilters', async () => {
    // Arrange
    mocks.fetchCatalogScenarios.mockResolvedValue([PHISHING]);
    renderPage();
    await waitFor(() => expect(cardTitles()).toHaveLength(1));
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'nothing like it' } });
    await waitFor(() => expect(screen.getByText('No scenario matches these filters.')).toBeTruthy());

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Reset filters' }));

    // Assert
    expect(cardTitles()).toHaveLength(1);
  });

  it('given_anInstalledScenario_should_removeItsCardAndNotify', async () => {
    // Arrange
    mocks.fetchCatalogScenarios.mockResolvedValue([PHISHING]);
    renderPage();
    await waitFor(() => expect(cardTitles()).toHaveLength(1));

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Install' }));
    fireEvent.click(screen.getByTestId('confirm-install'));

    // Assert
    expect(MESSAGING$.notifySuccess).toHaveBeenCalledWith('Phishing v1.0 installed');
    expect(screen.getByText('Every catalog scenario is installed')).toBeTruthy();
  });
});
