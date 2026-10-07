import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadUsageWarning, { type PayloadUsageWarningKind } from '../../../../../admin/components/threat_arsenal/approval/PayloadUsageWarning';
import { type ThreatArsenalActionUsageOutput } from '../../../../../utils/api-types';

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [key, val]) => text.replace(`{${key}}`, String(val)), value),
  }),
}));

const renderWarning = (usage: ThreatArsenalActionUsageOutput | undefined, kind: PayloadUsageWarningKind) => render(
  <MemoryRouter>
    <ThemeProvider theme={createTheme()}>
      <TooltipProvider>
        <PayloadUsageWarning usage={usage} kind={kind} />
      </TooltipProvider>
    </ThemeProvider>
  </MemoryRouter>,
);

describe('PayloadUsageWarning', () => {
  afterEach(() => {
    cleanup();
  });

  it('gives the counts and links the atomic testings, scenarios and simulations the user can open', () => {
    // Arrange
    const usage: ThreatArsenalActionUsageOutput = {
      usage_atomic_testings_count: 1,
      usage_scenarios_count: 7,
      usage_simulations_count: 1,
      usage_atomic_testings: [{
        id: 'atomic-1',
        name: 'Whoami check',
      }],
      usage_scenarios: [{
        id: 'scenario-1',
        name: 'Ransomware',
      }],
      usage_simulations: [{
        id: 'simulation-1',
        name: 'Q4 drill',
      }],
    };

    // Act
    renderWarning(usage, 'reject');

    // Assert
    expect(screen.queryByText('This payload is used in 1 atomic testings, 7 scenarios, 1 simulations. Rejecting it blocks their launch.')).not.toBeNull();
    expect(screen.getByText('Ransomware').closest('a')?.getAttribute('href')).toBe('/admin/scenarios/scenario-1');
    expect(screen.getByText('Q4 drill').closest('a')?.getAttribute('href')).toBe('/admin/simulations/simulation-1');
    expect(screen.queryByText(/and 6 more/)).not.toBeNull();
  });

  it('gives the counts only when the user cannot open them', () => {
    // Arrange
    const usage: ThreatArsenalActionUsageOutput = {
      usage_atomic_testings_count: 0,
      usage_scenarios_count: 2,
      usage_simulations_count: 0,
    };

    // Act
    renderWarning(usage, 'edit');

    // Assert
    expect(screen.queryByText(/Saving will send this payload back to Pending approval and block the launch of 0 atomic testings, 2 scenarios, 0 simulations/)).not.toBeNull();
    expect(screen.queryAllByRole('link')).toHaveLength(0);
  });

  it('renders nothing for a payload that is not used', () => {
    // Arrange / Act
    const { container } = renderWarning({
      usage_atomic_testings_count: 0,
      usage_scenarios_count: 0,
      usage_simulations_count: 0,
    }, 'reject');

    // Assert
    expect(container.textContent).toBe('');
  });
});
