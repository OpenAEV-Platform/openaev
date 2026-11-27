import { TooltipProvider } from '@filigran/design-system';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  devFeatures: [] as string[],
  launchScenario: vi.fn(),
}));

// A real hook, so a flag read outside render fails the way it does in the browser.
vi.mock('../../../../../store', async () => {
  const { useRef } = await import('react');
  const helper: Record<string, () => unknown> = {
    getScenario: () => ({
      scenario_id: 'chained',
      scenario_name: 'Chained',
      scenario_workflow_id: 'workflow',
    }),
    getPlatformSettings: () => ({ enabled_dev_features: mocks.devFeatures }),
    logged: () => ({}),
    getWorkflowConfiguration: () => undefined,
  };
  return {
    useHelper: <T,>(selector: (stub: Record<string, () => unknown>) => T): T => {
      useRef(null);
      return selector(new Proxy(helper, { get: (target, key: string) => target[key] ?? (() => []) }));
    },
  };
});
vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string) => key,
    locale: 'en',
    fld: (date: string) => date,
  }),
}));
vi.mock('../../../../../actions/scenarios/scenario-actions', () => ({
  dismissScenarioExpectationsDrift: vi.fn(),
  fetchScenario: vi.fn(),
  fetchScenarioExpectationsDrift: vi.fn().mockResolvedValue(null),
  fetchScenarioTeams: vi.fn(),
  launchScenario: mocks.launchScenario,
  realignScenarioExpectations: vi.fn(),
  searchScenarioHealthcheks: vi.fn().mockResolvedValue({ data: [] }),
  updateScenarioRecurrence: vi.fn(),
  fetchScenarioLaunchCheck: vi.fn().mockResolvedValue({
    launch_check_injects: [],
    launch_check_missing_requirements: [],
    launch_check_revoked_injects: [],
  }),
}));
vi.mock('../../../../../actions/chaining/chaining-actions', () => ({ fetchSteps: vi.fn().mockResolvedValue({ data: [] }) }));
vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../utils/hooks/useAuth', () => ({ default: () => ({ settings: {} }) }));
vi.mock('../../../../../utils/hooks/useDataLoader', () => ({ default: () => undefined }));
vi.mock('../../../../../utils/hooks/useEnterpriseEdition', () => ({
  default: () => ({
    isValidated: true,
    openDialog: vi.fn(),
    setEEFeatureDetectedInfo: vi.fn(),
  }),
}));
vi.mock('../../../../../utils/permissions/useScenarioPermissions', () => ({
  default: () => ({
    canLaunch: true,
    canManage: true,
    canDelete: true,
  }),
}));
vi.mock('../../../../../admin/components/scenarios/launch/useOpenLaunchedSimulation', () => ({ default: () => vi.fn() }));
vi.mock('../../../../../admin/components/scenarios/scenario/ScenarioPopover', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/scenarios/scenario/ScenarioConfiguration', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/autonomous/AutonomousRunConfigDrawer', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/common/healthchecks/HealthcheckIndicator', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/common/injects/expectations/ExpectationsDriftIndicator', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/graph/ExploreInGraphButton', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/profile/triggers/TriggerSubscribeButton', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/reporting/EntityReportsPanel', () => ({ default: () => null }));

const { default: ScenarioHeader } = await import('../../../../../admin/components/scenarios/scenario/ScenarioHeader');

const Header = () => {
  const [open, setOpen] = useState(false);
  return <ScenarioHeader openInstantiateSimulationAndStart={open} setOpenInstantiateSimulationAndStart={setOpen} />;
};

const renderHeader = (devFeatures: string[]) => {
  mocks.devFeatures = devFeatures;
  mocks.launchScenario.mockResolvedValue({ simulation_id: 'simulation' });
  return render(
    <TooltipProvider>
      <MemoryRouter initialEntries={['/admin/scenarios/chained']}>
        <Routes>
          <Route path="/admin/scenarios/:scenarioId" element={<Header />} />
        </Routes>
      </MemoryRouter>
    </TooltipProvider>,
  );
};

describe('ScenarioHeader normal launch of a chained scenario', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('opens the launch check and starts the simulation with the catalogue preview', async () => {
    // Arrange
    renderHeader(['CATALOG']);

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Normal' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Launch now' }));

    // Assert
    expect(mocks.launchScenario).toHaveBeenCalledWith('chained', expect.anything(), false);
  });

  it('asks for a confirmation without the catalogue preview', async () => {
    // Arrange
    renderHeader([]);

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Normal' }));

    // Assert
    expect(await screen.findByRole('button', { name: 'Confirm' })).toBeTruthy();
  });
});
