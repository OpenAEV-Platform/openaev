import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Buttons } from '../../../../../admin/components/simulations/simulation/ExerciseHeader';
import { type LaunchBlockerOutput } from '../../../../../utils/api-types';

vi.mock('../../../../../components/i18n', async importOriginal => ({
  ...(await importOriginal<object>()),
  useFormatter: () => ({
    t: (value: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [key, val]) => text.replace(`{${key}}`, String(val)), value),
  }),
}));
vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../utils/permissions/useSimulationPermissions', () => ({ default: () => ({ canLaunch: true }) }));

const renderStart = (launchBlockers: LaunchBlockerOutput[]) => render(
  <MemoryRouter>
    <ThemeProvider theme={createTheme()}>
      <TooltipProvider>
        <Buttons
          exerciseId="simulation-1"
          exerciseStatus="SCHEDULED"
          exerciseName="TEST-SCENARIO"
          onLoading={vi.fn()}
          isLoading={false}
          isScopeMissing={false}
          launchBlockers={launchBlockers}
        />
      </TooltipProvider>
    </ThemeProvider>
  </MemoryRouter>,
);

const blockers = (...names: string[]): LaunchBlockerOutput[] => names.map(name => ({
  id: name,
  name,
  approval_status: name.endsWith('R') ? 'REJECTED' : 'PENDING',
  reason: '',
}));

describe('Simulation Start now: blocked by actions that are not approved', () => {
  afterEach(() => {
    cleanup();
  });

  it('is disabled with a tooltip naming up to 3 blocking actions then "+N", and opens no confirm dialog', () => {
    // Arrange
    renderStart(blockers('TEST-A', 'TEST-B', 'TEST-R', 'TEST-D', 'TEST-E'));

    // Act
    const start = screen.getByRole('button', { name: 'Start now' }) as HTMLButtonElement;
    fireEvent.click(start);

    // Assert
    expect(start.disabled).toBe(true);
    expect(screen.getByLabelText('Can\'t launch: TEST-A is pending approval, TEST-B is pending approval, TEST-R is rejected +2')).toBeTruthy();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('is enabled again once everything is approved and opens the confirm dialog', () => {
    // Arrange
    renderStart([]);

    // Act
    const start = screen.getByRole('button', { name: 'Start now' }) as HTMLButtonElement;
    fireEvent.click(start);

    // Assert
    expect(start.disabled).toBe(false);
    expect(screen.getByRole('dialog')).toBeTruthy();
  });
});
