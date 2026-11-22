import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type ScenarioRow } from '../../../../../admin/components/scenarios/scenarioRow';
import { type ScenarioTargets } from '../../../../../admin/components/scenarios/targets/scenarioTargets';

const { launchScenario, navigate, notifySuccess, drawerProps } = vi.hoisted(() => ({
  launchScenario: vi.fn((_scenarioId: string, _targets?: unknown) => Promise.resolve({ simulation_id: 'simulation-id' })),
  navigate: vi.fn(),
  notifySuccess: vi.fn(),
  drawerProps: { onSubmit: undefined as undefined | ((targets: ScenarioTargets) => Promise<unknown>) },
}));

vi.mock('react-router', () => ({ useNavigate: () => navigate }));
vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));
vi.mock('../../../../../actions/scenarios/scenario-actions', () => ({ launchScenario }));
vi.mock('../../../../../utils/Environment', () => ({ MESSAGING$: { notifySuccess } }));
vi.mock('../../../../../admin/components/scenarios/targets/ScenarioTargetsDrawer', () => ({
  default: (props: { onSubmit: (targets: ScenarioTargets) => Promise<unknown> }) => {
    drawerProps.onSubmit = props.onSubmit;
    return null;
  },
}));

// Imported after the mocks so the component picks them up.
const { default: ScenarioLaunchDrawer } = await import('../../../../../admin/components/scenarios/launch/ScenarioLaunchDrawer');

const TARGETS: ScenarioTargets = {
  assets: [{
    id: 'asset-id',
    label: 'Asset',
  }],
  assetGroups: [],
  teams: [],
  manual: 'host.local',
};

const launchWith = async (scenario: { scenario_workflow_id?: string }) => {
  render(
    <ScenarioLaunchDrawer
      scenario={{
        scenario_id: 'scenario-id',
        scenario_name: 'Scenario',
        ...scenario,
      } as ScenarioRow}
      onClose={vi.fn()}
    />,
  );
  await drawerProps.onSubmit?.(TARGETS);
};

describe('ScenarioLaunchDrawer', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('launches the scenario with the chosen targets and opens the simulation', async () => {
    // Act
    await launchWith({});

    // Assert
    expect(launchScenario).toHaveBeenCalledWith('scenario-id', {
      asset_ids: ['asset-id'],
      asset_group_ids: [],
      team_ids: [],
      manual_targets: ['host.local'],
    });
    expect(notifySuccess).toHaveBeenCalledWith('New simulation successfully created and started');
    expect(navigate).toHaveBeenCalledWith('/admin/simulations/simulation-id');
  });

  it('opens a chained simulation on its attack path', async () => {
    // Act
    await launchWith({ scenario_workflow_id: 'workflow-id' });

    // Assert
    expect(navigate).toHaveBeenCalledWith('/admin/simulations/simulation-id/attack-path');
  });
});
