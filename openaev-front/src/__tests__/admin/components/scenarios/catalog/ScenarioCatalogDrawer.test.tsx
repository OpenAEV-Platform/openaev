import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type CatalogScenarioOutput } from '../../../../../utils/api-types';

const mocks = vi.hoisted(() => ({ canManageSettings: true }));

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [name, value]) => text.replace(`{${name}}`, String(value)), key),
    nsd: (date: string) => date,
  }),
}));
vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => mocks.canManageSettings }) }));
vi.mock('../../../../../admin/components/scenarios/catalog/ScenarioCatalogFacts', () => ({ default: ({ values }: { values: unknown }) => <pre data-testid="scenario-facts">{JSON.stringify(values)}</pre> }));
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
  catalog_scenario_payloads: [],
};

const NMAP = {
  requirement_label: 'Injector openaev_nmap',
  requirement_name: 'Nmap',
  requirement_integration_id: 'nmap-integration',
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

const installButton = () => screen.getByRole('button', { name: 'Install' }) as HTMLButtonElement;

describe('ScenarioCatalogDrawer', () => {
  afterEach(() => {
    cleanup();
    mocks.canManageSettings = true;
  });

  describe('summary', () => {
    it('given_aScenario_should_sumItUpInOneLineAndShowTheChangesOfItsVersion', () => {
      // Act
      renderDrawer({
        ...SCENARIO,
        catalog_scenario_published_at: '2026-01-15',
        catalog_scenario_injects_count: 7,
        catalog_scenario_changelog: 'New phishing mail',
      });

      // Assert
      expect(screen.getByText('v1.2 · 7 injects · Published 2026-01-15 · by Filigran')).toBeTruthy();
      expect(screen.getByText('New phishing mail')).toBeTruthy();
      expect(screen.getByText('What\'s new in v1.2')).toBeTruthy();
    });

    it('given_aScenarioWithItsFocusAndSeverity_should_showThemAmongItsFacts', () => {
      // Act
      renderDrawer({
        ...SCENARIO,
        catalog_scenario_category: 'custom-category',
        catalog_scenario_main_focus: 'endpoint-protection',
        catalog_scenario_severity: 'high',
      });

      // Assert
      expect(JSON.parse(screen.getByTestId('scenario-facts').textContent ?? '{}')).toEqual({
        scenario_name: 'Phishing',
        scenario_description: '',
        scenario_category: 'custom-category',
        scenario_main_focus: 'endpoint-protection',
        scenario_severity: 'high',
        scenario_tags: [],
      });
    });

    it('given_noOptionalFacts_should_showTheVersionOnly', () => {
      // Act
      renderDrawer({
        ...SCENARIO,
        catalog_scenario_author: undefined,
        catalog_scenario_platforms: [],
      });

      // Assert
      expect(screen.getByText('v1.2')).toBeTruthy();
      expect(screen.queryByText(/What's new/)).toBeNull();
      expect(screen.queryByText('Payloads')).toBeNull();
    });
  });

  describe('missing requirements', () => {
    it('given_missingRequirements_should_linkEachToItsIntegrationAndKeepTheInstall', () => {
      // Act
      renderDrawer({
        ...SCENARIO,
        catalog_scenario_missing_requirements: [NMAP, {
          requirement_label: 'OpenAEV >=2.0',
          requirement_name: 'OpenAEV >=2.0',
        }],
      });

      // Assert
      expect(screen.getByText('Missing to run it')).toBeTruthy();
      expect(screen.getByText('Nmap is missing on this platform')).toBeTruthy();
      expect(screen.getByText('OpenAEV >=2.0 is missing on this platform')).toBeTruthy();
      const links = screen.getAllByRole('link', { name: 'Enable integration' });
      expect(links).toHaveLength(1);
      expect(links[0].getAttribute('href')).toBe('/admin/integrations/catalog/nmap-integration');
      expect(installButton().disabled).toBe(false);
    });

    it('given_anIntegrationNotRunning_should_sayIt', () => {
      // Act
      renderDrawer({
        ...SCENARIO,
        catalog_scenario_missing_requirements: [{
          ...NMAP,
          requirement_status: 'NOT_RUNNING',
        }],
      });

      // Assert
      expect(screen.getByText('Nmap is not running')).toBeTruthy();
    });

    it('given_noRightToManageIntegrations_should_notOfferToEnableThem', () => {
      // Arrange
      mocks.canManageSettings = false;

      // Act
      renderDrawer({
        ...SCENARIO,
        catalog_scenario_missing_requirements: [NMAP],
      });

      // Assert
      expect(screen.queryByRole('link', { name: 'Enable integration' })).toBeNull();
    });
  });

  describe('payloads', () => {
    it('given_payloads_should_listThemByNameOrId', () => {
      // Act
      renderDrawer({
        ...SCENARIO,
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
      });

      // Assert
      expect(screen.getByText('Nmap scan · v1.0')).toBeTruthy();
      expect(screen.getByText('unnamed · v2.1')).toBeTruthy();
      expect(screen.getByText('Payloads')).toBeTruthy();
    });
  });

  describe('footer', () => {
    it('given_installClicked_should_installTheScenario', () => {
      // Arrange
      const onInstall = vi.fn();
      renderDrawer(SCENARIO, { onInstall });

      // Act
      fireEvent.click(installButton());

      // Assert
      expect(onInstall).toHaveBeenCalledWith(SCENARIO);
    });

    it('given_noRightToInstall_should_disableTheInstall', () => {
      // Act
      renderDrawer(SCENARIO, { canInstall: false });

      // Assert
      expect(installButton().disabled).toBe(true);
    });

    it('given_cancelClicked_should_closeTheDrawer', () => {
      // Arrange
      const onClose = vi.fn();
      renderDrawer(SCENARIO, { onClose });

      // Act
      fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

      // Assert
      expect(onClose).toHaveBeenCalled();
    });
  });

  it('given_noScenario_should_showNothing', () => {
    // Act
    const { container } = renderDrawer(null);

    // Assert
    expect(container.childElementCount).toBe(0);
  });
});
