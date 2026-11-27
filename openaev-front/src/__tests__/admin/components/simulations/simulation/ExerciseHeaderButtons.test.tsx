import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Buttons } from '../../../../../admin/components/simulations/simulation/ExerciseHeader';

vi.mock('../../../../../components/i18n', async importOriginal => ({
  ...(await importOriginal<object>()),
  useFormatter: () => ({
    t: (value: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [key, val]) => text.replace(`{${key}}`, String(val)), value),
  }),
}));
vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../utils/permissions/useSimulationPermissions', () => ({ default: () => ({ canLaunch: true }) }));

const renderStart = () => render(
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
        />
      </TooltipProvider>
    </ThemeProvider>
  </MemoryRouter>,
);

// A non-approved action no longer blocks the launch: the simulation starts and the injects on
// that version fail when they run, like a revoked payload.
describe('Simulation Start now', () => {
  afterEach(() => {
    cleanup();
  });

  it('is enabled and opens the confirm dialog', () => {
    // Arrange
    renderStart();

    // Act
    const start = screen.getByRole('button', { name: 'Start now' }) as HTMLButtonElement;
    fireEvent.click(start);

    // Assert
    expect(start.disabled).toBe(false);
    expect(screen.queryByLabelText(/Can't launch/)).toBeNull();
    expect(screen.getByRole('dialog')).toBeTruthy();
  });
});
