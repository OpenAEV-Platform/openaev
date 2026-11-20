import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type CatalogScenarioOutput } from '../../../../../utils/api-types';

const mocks = vi.hoisted(() => ({ fetchCatalogScenarioVersions: vi.fn() }));

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [name, value]) => text.replace(`{${name}}`, String(value)), key),
    nsd: (date: string) => date,
  }),
}));
vi.mock('../../../../../actions/catalog/scenario-catalog-actions', () => ({ fetchCatalogScenarioVersions: mocks.fetchCatalogScenarioVersions }));
vi.mock('../../../../../components/common/Drawer', () => ({
  default: ({ open, headerActions, children }: {
    open: boolean;
    headerActions: ReactNode;
    children: ReactNode;
  }) => (open
    ? (
        <div>
          {headerActions}
          {children}
        </div>
      )
    : null),
}));

const { default: ScenarioCatalogDrawer } = await import('../../../../../admin/components/scenarios/catalog/ScenarioCatalogDrawer');

const SCENARIO: CatalogScenarioOutput = {
  catalog_scenario_external_id: 'phishing',
  catalog_scenario_name: 'Phishing',
  catalog_scenario_type: 'TIME_BASED',
  catalog_scenario_version: '1.2',
  catalog_scenario_category: 'attack-scenario',
  catalog_scenario_author: 'Filigran',
  catalog_scenario_platforms: ['Windows'],
  catalog_scenario_missing_requirements: [],
};

const renderDrawer = (scenario: CatalogScenarioOutput | null, onInstall = vi.fn()) => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <ScenarioCatalogDrawer scenario={scenario} canInstall onClose={vi.fn()} onInstall={onInstall} />
    </TooltipProvider>
  </ThemeProvider>,
);

describe('ScenarioCatalogDrawer', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given_aScenario_should_showItsFactsAndTheChangesOfItsVersion', async () => {
    // Arrange
    mocks.fetchCatalogScenarioVersions.mockResolvedValue([{
      catalog_scenario_version: '1.2',
      catalog_scenario_injects_count: 7,
      catalog_scenario_changelog: 'New phishing mail',
    }]);

    // Act
    renderDrawer(SCENARIO);

    // Assert
    expect(await screen.findByText('New phishing mail')).toBeTruthy();
    expect(screen.getByText('What\'s new in v1.2')).toBeTruthy();
    expect(screen.getByText('7')).toBeTruthy();
    expect(screen.getByText('v1.2')).toBeTruthy();
    expect(screen.getByText('Attack Scenario')).toBeTruthy();
    expect(mocks.fetchCatalogScenarioVersions).toHaveBeenCalledWith('phishing');
  });

  it('given_aVersionWithItsFocusAndSeverity_should_showThem', async () => {
    // Arrange
    mocks.fetchCatalogScenarioVersions.mockResolvedValue([{
      catalog_scenario_version: '1.2',
      catalog_scenario_main_focus: 'endpoint-protection',
      catalog_scenario_severity: 'high',
    }]);

    // Act
    renderDrawer({
      ...SCENARIO,
      catalog_scenario_published_at: '2026-01-15',
      catalog_scenario_category: 'custom-category',
    });

    // Assert
    expect(await screen.findByText('endpoint-protection')).toBeTruthy();
    expect(screen.getByText('high')).toBeTruthy();
    expect(screen.getByText('2026-01-15')).toBeTruthy();
    expect(screen.getByText('custom-category')).toBeTruthy();
  });

  it('given_noOptionalFactsAndNoVersions_should_showDashes', async () => {
    // Arrange
    mocks.fetchCatalogScenarioVersions.mockRejectedValue(new Error('unreachable'));

    // Act
    renderDrawer({
      ...SCENARIO,
      catalog_scenario_author: undefined,
      catalog_scenario_category: undefined,
      catalog_scenario_platforms: [],
    });

    // Assert
    await waitFor(() => expect(screen.getAllByText('–')).toHaveLength(6));
    expect(screen.getByText('-')).toBeTruthy();
    expect(screen.queryByText(/What's new/)).toBeNull();
  });

  it('given_installClicked_should_installTheScenario', async () => {
    // Arrange
    mocks.fetchCatalogScenarioVersions.mockResolvedValue([]);
    const onInstall = vi.fn();
    renderDrawer(SCENARIO, onInstall);

    // Act
    fireEvent.click(await screen.findByRole('button', { name: 'Install' }));

    // Assert
    expect(onInstall).toHaveBeenCalledWith(SCENARIO);
  });

  it('given_missingRequirements_should_listThemAndBlockTheInstall', async () => {
    // Arrange
    mocks.fetchCatalogScenarioVersions.mockResolvedValue([]);

    // Act
    renderDrawer({
      ...SCENARIO,
      catalog_scenario_missing_requirements: ['Nmap'],
    });

    // Assert
    expect(await screen.findByText('Nmap')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Install' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('given_noScenario_should_showNothingNorFetch', () => {
    // Act
    const { container } = renderDrawer(null);

    // Assert
    expect(container.childElementCount).toBe(0);
    expect(mocks.fetchCatalogScenarioVersions).not.toHaveBeenCalled();
  });
});
