import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock('../../../../../admin/components/common/form/useAskAiAvailability', () => ({
  default: () => ({
    hidden: true,
    isAvailable: false,
  }),
}));
vi.mock('../../../../../admin/components/common/filters/DefaultKillChainSelectField', () => ({ default: ({ disabled }: { disabled?: boolean }) => <select aria-label="Default kill chain" disabled={disabled} /> }));

const { default: ScenarioCatalogFacts } = await import('../../../../../admin/components/scenarios/catalog/ScenarioCatalogFacts');

const VALUES = {
  scenario_name: 'Phishing',
  scenario_description: 'A phishing campaign',
  scenario_category: 'attack-scenario',
  scenario_tags: [],
};

describe('ScenarioCatalogFacts', () => {
  afterEach(() => {
    cleanup();
  });

  it('given_aCatalogueScenario_should_showItsFactsInDisabledFieldsWithNothingToSubmit', () => {
    // Act
    const { container } = render(
      <ThemeProvider theme={createTheme()}>
        <TooltipProvider>
          <ScenarioCatalogFacts values={VALUES} />
        </TooltipProvider>
      </ThemeProvider>,
    );

    // Assert
    expect(screen.getByDisplayValue('Phishing')).toBeTruthy();
    expect(screen.getByDisplayValue('A phishing campaign')).toBeTruthy();
    const fields = [...container.querySelectorAll('input:not([aria-hidden]):not([type=hidden]), textarea'), ...screen.getAllByRole('combobox')];
    expect(fields.length).toBeGreaterThan(0);
    fields.forEach(field => expect((field as HTMLInputElement).disabled).toBe(true));
    expect(screen.queryByText('Tags')).toBeNull();
    expect(screen.queryAllByRole('button', { name: /Cancel|Create|Update/ })).toHaveLength(0);
  });
});
