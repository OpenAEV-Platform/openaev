import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
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
vi.mock('../../../../../admin/components/scenarios/ScenarioForm', () => ({
  default: ({ readOnly, initialValues }: {
    readOnly?: boolean;
    initialValues: unknown;
  }) => <pre data-testid="scenario-form" data-read-only={String(readOnly)}>{JSON.stringify(initialValues)}</pre>,
}));
vi.mock('../../../../../components/common/Drawer', () => ({
  default: ({ open, children }: {
    open: boolean;
    children: ReactNode;
  }) => (open ? <div>{children}</div> : null),
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

const renderDrawer = (scenario: CatalogScenarioOutput | null, {
  canInstall = true,
  onInstall = vi.fn(),
  onClose = vi.fn(),
} = {}) => render(
  <MemoryRouter>
    <ThemeProvider theme={createTheme()}>
      <TooltipProvider>
        <ScenarioCatalogDrawer scenario={scenario} canInstall={canInstall} onClose={onClose} onInstall={onInstall} />
      </TooltipProvider>
    </ThemeProvider>
  </MemoryRouter>,
);

const formValues = async () => JSON.parse((await screen.findByTestId('scenario-form')).textContent ?? '{}');

const installButton = () => screen.getByRole('button', { name: 'Install' }) as HTMLButtonElement;

describe('ScenarioCatalogDrawer', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('summary', () => {
    it('given_aScenario_should_sumItUpInOneLineAndShowTheChangesOfItsVersion', async () => {
      // Arrange
      mocks.fetchCatalogScenarioVersions.mockResolvedValue([{
        catalog_scenario_version: '1.2',
        catalog_scenario_injects_count: 7,
        catalog_scenario_changelog: 'New phishing mail',
        catalog_scenario_payloads: [],
      }]);

      // Act
      renderDrawer({
        ...SCENARIO,
        catalog_scenario_published_at: '2026-01-15',
      });

      // Assert
      expect(await screen.findByText('v1.2 · 7 injects · Published 2026-01-15 · by Filigran')).toBeTruthy();
      expect(screen.getByText('New phishing mail')).toBeTruthy();
      expect(screen.getByText('What\'s new in v1.2')).toBeTruthy();
      expect(mocks.fetchCatalogScenarioVersions).toHaveBeenCalledWith('phishing');
    });

    it('given_aVersionWithItsFocusAndSeverity_should_showThemInTheReadOnlyForm', async () => {
      // Arrange
      mocks.fetchCatalogScenarioVersions.mockResolvedValue([{
        catalog_scenario_version: '1.2',
        catalog_scenario_main_focus: 'endpoint-protection',
        catalog_scenario_severity: 'high',
        catalog_scenario_payloads: [],
      }]);

      // Act
      renderDrawer({
        ...SCENARIO,
        catalog_scenario_category: 'custom-category',
      });

      // Assert
      expect(await formValues()).toEqual({
        scenario_name: 'Phishing',
        scenario_description: '',
        scenario_category: 'custom-category',
        scenario_main_focus: 'endpoint-protection',
        scenario_severity: 'high',
        scenario_tags: [],
      });
      expect(screen.getByTestId('scenario-form').dataset.readOnly).toBe('true');
    });

    it('given_noOptionalFactsAndNoVersions_should_showTheVersionAndTheNameOnly', async () => {
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
      expect(await formValues()).toEqual({
        scenario_name: 'Phishing',
        scenario_description: '',
        scenario_tags: [],
      });
      expect(screen.getByText('v1.2')).toBeTruthy();
      expect(screen.queryByText(/What's new/)).toBeNull();
      expect(screen.queryByText('Payloads')).toBeNull();
    });
  });

  describe('missing requirements', () => {
    it('given_missingRequirements_should_linkEachToItsIntegrationAndBlockTheInstall', async () => {
      // Arrange
      mocks.fetchCatalogScenarioVersions.mockResolvedValue([]);

      // Act
      renderDrawer({
        ...SCENARIO,
        catalog_scenario_missing_requirements: [
          {
            requirement_label: 'Injector openaev_nmap',
            requirement_name: 'Nmap',
            requirement_integration_id: 'nmap-integration',
          },
          {
            requirement_label: 'OpenAEV >=2.0',
            requirement_name: 'OpenAEV >=2.0',
          },
        ],
      });

      // Assert
      expect(await screen.findByText('Missing to run it')).toBeTruthy();
      expect(screen.getByText('Nmap')).toBeTruthy();
      expect(screen.getByText('OpenAEV >=2.0')).toBeTruthy();
      const links = screen.getAllByRole('link', { name: 'Enable integration' });
      expect(links).toHaveLength(1);
      expect(links[0].getAttribute('href')).toBe('/admin/integrations/catalog/nmap-integration');
      expect(installButton().disabled).toBe(true);
    });

    it('given_missingIntegrationsWithTheirState_should_tellWhetherEachIsDeployed', async () => {
      // Arrange
      mocks.fetchCatalogScenarioVersions.mockResolvedValue([]);

      // Act
      renderDrawer({
        ...SCENARIO,
        catalog_scenario_missing_requirements: [
          {
            requirement_label: 'Injector openaev_nmap',
            requirement_name: 'Nmap',
            requirement_integration_id: 'nmap-integration',
            requirement_status: 'NOT_RUNNING',
          },
          {
            requirement_label: 'Injector openaev_nuclei',
            requirement_name: 'Injector openaev_nuclei',
            requirement_status: 'NOT_DEPLOYED',
          },
          {
            requirement_label: 'OpenAEV >=2.0',
            requirement_name: 'OpenAEV >=2.0',
          },
        ],
      });

      // Assert
      expect(await screen.findByText('Not running')).toBeTruthy();
      expect(screen.getByText('Not deployed')).toBeTruthy();
      expect(screen.getAllByText(/^Not (running|deployed)$/)).toHaveLength(2);
    });
  });

  describe('payloads', () => {
    it('given_aVersionWithPayloads_should_listThemByNameOrId', async () => {
      // Arrange
      mocks.fetchCatalogScenarioVersions.mockResolvedValue([{
        catalog_scenario_version: '1.2',
        catalog_scenario_payloads: [
          {
            catalog_payload_external_id: 'nmap-scan',
            catalog_payload_version: '1.0',
            catalog_payload_name: 'Nmap scan',
            catalog_payload_platforms: [],
          },
          {
            catalog_payload_external_id: 'unnamed',
            catalog_payload_version: '2.1',
            catalog_payload_platforms: [],
          },
        ],
      }]);

      // Act
      renderDrawer(SCENARIO);

      // Assert
      expect(await screen.findByText('Nmap scan · v1.0')).toBeTruthy();
      expect(screen.getByText('unnamed · v2.1')).toBeTruthy();
      expect(screen.getByText('Payloads')).toBeTruthy();
    });
  });

  describe('footer', () => {
    it('given_installClicked_should_installTheScenario', async () => {
      // Arrange
      mocks.fetchCatalogScenarioVersions.mockResolvedValue([]);
      const onInstall = vi.fn();
      renderDrawer(SCENARIO, { onInstall });

      // Act
      fireEvent.click(await screen.findByRole('button', { name: 'Install' }));

      // Assert
      expect(onInstall).toHaveBeenCalledWith(SCENARIO);
    });

    it('given_noRightToInstall_should_disableTheInstall', async () => {
      // Arrange
      mocks.fetchCatalogScenarioVersions.mockResolvedValue([]);

      // Act
      renderDrawer(SCENARIO, { canInstall: false });

      // Assert
      await waitFor(() => expect(mocks.fetchCatalogScenarioVersions).toHaveBeenCalled());
      expect(installButton().disabled).toBe(true);
    });

    it('given_cancelClicked_should_closeTheDrawer', async () => {
      // Arrange
      mocks.fetchCatalogScenarioVersions.mockResolvedValue([]);
      const onClose = vi.fn();
      renderDrawer(SCENARIO, { onClose });

      // Act
      fireEvent.click(await screen.findByRole('button', { name: 'Cancel' }));

      // Assert
      expect(onClose).toHaveBeenCalled();
    });
  });

  it('given_noScenario_should_showNothingNorFetch', () => {
    // Act
    const { container } = renderDrawer(null);

    // Assert
    expect(container.childElementCount).toBe(0);
    expect(mocks.fetchCatalogScenarioVersions).not.toHaveBeenCalled();
  });
});
