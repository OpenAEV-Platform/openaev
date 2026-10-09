import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import PayloadUsage from '../../../../../admin/components/threat_arsenal/approval/PayloadUsage';
import { type ThreatArsenalActionUsageItem, type ThreatArsenalActionUsageOutput } from '../../../../../utils/api-types';
import en from '../../../../../utils/lang/en.json';
import fr from '../../../../../utils/lang/fr.json';
import ru from '../../../../../utils/lang/ru.json';

// Real react-intl and real catalogs: the plural forms are the translated ICU messages.
const renderUsage = (usage: ThreatArsenalActionUsageOutput | undefined, locale = 'en', messages: Record<string, string> = en) => render(
  <IntlProvider locale={locale} defaultLocale="en" messages={messages} onError={() => {}}>
    <MemoryRouter>
      <ThemeProvider theme={createTheme()}>
        <PayloadUsage usage={usage} />
      </ThemeProvider>
    </MemoryRouter>
  </IntlProvider>,
);

const items = (prefix: string, count: number): ThreatArsenalActionUsageItem[] => Array.from({ length: count }, (_, index) => ({
  id: `${prefix}-${index + 1}`,
  name: `${prefix} ${index + 1}`,
}));

// The backend lists at most 20 names per type; the counts are exact.
const usage = (atomicTestings: number, scenarios: number, simulations: number): ThreatArsenalActionUsageOutput => ({
  usage_atomic_testings_count: atomicTestings,
  usage_scenarios_count: scenarios,
  usage_simulations_count: simulations,
  usage_atomic_testings: items('Atomic', Math.min(atomicTestings, 20)),
  usage_scenarios: items('Scenario', Math.min(scenarios, 20)),
  usage_simulations: items('Simulation', Math.min(simulations, 20)),
});

describe('PayloadUsage', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders nothing when the payload is not used (0 items)', () => {
    // Arrange / Act
    const { container } = renderUsage(usage(0, 0, 0));

    // Assert
    expect(container.textContent).toBe('');
  });

  it('uses the singular for 1 item and does not mention types with 0 items', () => {
    // Arrange / Act
    renderUsage(usage(1, 0, 0));

    // Assert
    expect(screen.getByText('Used in 1 atomic testing.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Atomic testings (1)' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Scenarios/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Simulations/ })).toBeNull();
  });

  it('groups 3 items by type, each group with its count and links opening in a new tab', () => {
    // Arrange / Act
    renderUsage(usage(1, 2, 0));

    // Assert
    expect(screen.getByText('Used in 1 atomic testing, 2 scenarios.')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Scenarios (2)' }));
    const scenarios = screen.getByRole('list', { name: 'Scenarios' });
    const links = within(scenarios).getAllByRole('link');
    expect(links.map(link => link.textContent)).toEqual(['Scenario 1', 'Scenario 2']);
    expect(links[0].getAttribute('href')).toBe('/admin/scenarios/Scenario-1');
    expect(links[0].getAttribute('target')).toBe('_blank');
  });

  it('keeps 50 items scalable: exact count, capped scrollable list and "Showing 20 of 50"', () => {
    // Arrange / Act
    renderUsage(usage(0, 0, 50));

    // Assert
    expect(screen.getByText('Used in 50 simulations.')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Simulations (50)' }));
    const list = screen.getByRole('list', { name: 'Simulations' });
    expect(within(list).getAllByRole('link')).toHaveLength(20);
    expect(list.style.overflowY).toBe('auto');
    expect(screen.getByText('Showing 20 of 50')).toBeTruthy();
  });

  it('gives the counts only when the user cannot open the items', () => {
    // Arrange / Act
    renderUsage({
      usage_atomic_testings_count: 0,
      usage_scenarios_count: 2,
      usage_simulations_count: 0,
    });

    // Assert
    expect(screen.getByText('Used in 2 scenarios.')).toBeTruthy();
    expect(screen.queryAllByRole('link')).toHaveLength(0);
    expect(screen.queryByRole('button', { name: /Scenarios/ })).toBeNull();
  });

  it('uses each language\'s plural forms', () => {
    // French: one / other
    renderUsage(usage(1, 3, 0), 'fr', fr);
    expect(screen.getByText('Utilisé dans 1 test atomique, 3 scénarios.')).toBeTruthy();
    cleanup();

    // Russian: one / few / many
    renderUsage(usage(1, 3, 5), 'ru', ru);
    expect(screen.getByText('Используется в: 1 атомный тест, 3 сценария, 5 симуляций.')).toBeTruthy();
  });
});
