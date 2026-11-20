import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type CatalogScenarioOutput, type CatalogScenarioRequirementOutput } from '../../../../../utils/api-types';

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [name, value]) => text.replace(`{${name}}`, String(value)), key),
    nsd: (date: string) => date,
  }),
}));

const { default: ScenarioCatalogCard } = await import('../../../../../admin/components/scenarios/catalog/ScenarioCatalogCard');

const requirement = (label: string): CatalogScenarioRequirementOutput => ({ requirement_label: label });

const scenarioMissing = (missing: CatalogScenarioRequirementOutput[]): CatalogScenarioOutput => ({
  catalog_scenario_external_id: 'phishing',
  catalog_scenario_name: 'Phishing',
  catalog_scenario_type: 'TIME_BASED',
  catalog_scenario_version: '1.2',
  catalog_scenario_platforms: [],
  catalog_scenario_missing_requirements: missing,
});

const renderCard = (scenario: CatalogScenarioOutput) => render(
  <MemoryRouter>
    <ThemeProvider theme={createTheme()}>
      <TooltipProvider>
        <ScenarioCatalogCard scenario={scenario} canInstall onInstall={vi.fn()} onOpen={vi.fn()} />
      </TooltipProvider>
    </ThemeProvider>
  </MemoryRouter>,
);

describe('ScenarioCatalogCard', () => {
  afterEach(() => {
    cleanup();
  });

  describe('missing requirements', () => {
    it.each<[string, CatalogScenarioRequirementOutput[], string]>([
      ['oneMissing', [requirement('Injector openaev_nmap')], 'Needs Injector openaev_nmap'],
      ['twoMissing', [requirement('Injector openaev_nmap'), requirement('OpenAEV >=2.0')], 'Needs Injector openaev_nmap, OpenAEV >=2.0'],
      ['threeMissing', [requirement('Injector a'), requirement('Injector b'), requirement('Injector c')], 'Needs 3 prerequisites'],
    ])('given_%s_should_nameOrCountThemAndBlockTheInstall', (_case, missing, label) => {
      // Act
      renderCard(scenarioMissing(missing));

      // Assert
      expect(screen.getByText(label)).toBeTruthy();
      expect((screen.getByRole('button', { name: 'Install' }) as HTMLButtonElement).disabled).toBe(true);
    });

    it('given_nothingMissing_should_showNoChipAndAllowTheInstall', () => {
      // Act
      renderCard(scenarioMissing([]));

      // Assert
      expect(screen.queryByText(/^Needs/)).toBeNull();
      expect((screen.getByRole('button', { name: 'Install' }) as HTMLButtonElement).disabled).toBe(false);
    });
  });
});
