import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Product decision (#307): the chaining engine (queue-based) cannot pause a run, so the Pause CTA
// must not exist for a chained simulation. Stop stays available in every case.
const mocks = vi.hoisted(() => ({
  canLaunch: vi.fn(),
  dispatch: vi.fn(),
  updateExerciseStatus: vi.fn(),
  reconcileExerciseInjects: vi.fn(),
}));

// Only useFormatter is overridden: the module graph pulled in by ExerciseHeader also relies on the
// default export (inject18n), which must keep working.
vi.mock('../../../../../components/i18n', async (importOriginal) => {
  const actual = await importOriginal() as Record<string, unknown>;
  return {
    ...actual,
    useFormatter: () => ({
      t: (value: string) => value,
      fldt: (value: string) => value,
    }),
  };
});

vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => mocks.dispatch }));

vi.mock('../../../../../utils/permissions/useSimulationPermissions', () => ({ default: () => ({ canLaunch: mocks.canLaunch() }) }));

vi.mock('../../../../../actions/Exercise', () => ({
  updateExerciseStatus: mocks.updateExerciseStatus,
  fetchExerciseTeams: vi.fn(),
  fetchExerciseExpectationsDrift: vi.fn(),
  dismissExerciseExpectationsDrift: vi.fn(),
  realignExerciseExpectations: vi.fn(),
  searchExerciseHealthchecks: vi.fn(),
}));

vi.mock('../../../../../actions/Inject', () => ({
  reconcileExerciseInjects: mocks.reconcileExerciseInjects,
  fetchExerciseInjectsSimple: vi.fn(),
}));

import { Buttons } from '../../../../../admin/components/simulations/simulation/ExerciseHeader';

const renderButtons = (props: {
  exerciseStatus: 'RUNNING' | 'PAUSED';
  isChaining: boolean;
}) => {
  render(
    <ThemeProvider theme={createTheme()}>
      <Buttons
        exerciseId="exercise-1"
        exerciseStatus={props.exerciseStatus}
        exerciseName="Simulation test"
        onLoading={vi.fn()}
        isLoading={false}
        isScopeMissing={false}
        isChaining={props.isChaining}
      />
    </ThemeProvider>,
  );
};

describe('ExerciseHeader lifecycle buttons', () => {
  beforeEach(() => {
    mocks.canLaunch.mockReturnValue(true);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('hides Pause for a running chained simulation but keeps Stop', () => {
    // Act
    renderButtons({
      exerciseStatus: 'RUNNING',
      isChaining: true,
    });

    // Assert
    expect(screen.queryByRole('button', { name: 'Pause' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Stop' })).toBeDefined();
  });

  it('shows Pause and Stop for a running non-chained simulation', () => {
    // Act
    renderButtons({
      exerciseStatus: 'RUNNING',
      isChaining: false,
    });

    // Assert
    expect(screen.getByRole('button', { name: 'Pause' })).toBeDefined();
    expect(screen.getByRole('button', { name: 'Stop' })).toBeDefined();
  });

  it('keeps Resume available for a chained simulation already paused in database', () => {
    // Act
    renderButtons({
      exerciseStatus: 'PAUSED',
      isChaining: true,
    });

    // Assert
    expect(screen.getByRole('button', { name: 'Resume' })).toBeDefined();
    expect(screen.getByRole('button', { name: 'Stop' })).toBeDefined();
  });
});
