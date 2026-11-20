import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type ScenarioRow } from '../../../../../admin/components/scenarios/scenarioRow';
import { type ScenarioTargets } from '../../../../../admin/components/scenarios/targets/scenarioTargets';
import { MESSAGING$ } from '../../../../../utils/Environment';

const mocks = vi.hoisted(() => ({
  launchScenario: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock(import('react-router'), async importOriginal => ({
  ...await importOriginal(),
  useNavigate: () => mocks.navigate,
}));
vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock('../../../../../actions/scenarios/scenario-actions', () => ({ launchScenario: mocks.launchScenario }));
vi.mock('../../../../../admin/components/scenarios/targets/ScenarioTargetsDrawer', () => ({
  default: ({ withTargets, onSubmit }: {
    withTargets: boolean;
    onSubmit: (targets: ScenarioTargets) => Promise<unknown>;
  }) => (
    <button
      type="button"
      data-testid="submit"
      data-with-targets={String(withTargets)}
      onClick={() => onSubmit({
        assets: [{
          id: 'asset-1',
          label: 'Web server',
        }],
        assetGroups: [],
        teams: [],
        manual: '10.0.0.1, app.example.com',
      })}
    />
  ),
}));

const { default: ScenarioLaunchDrawer } = await import('../../../../../admin/components/scenarios/launch/ScenarioLaunchDrawer');

const scenario = (workflowId?: string) => ({
  scenario_id: 'scenario-1',
  scenario_name: 'Phishing',
  scenario_workflow_id: workflowId,
}) as ScenarioRow;

describe('ScenarioLaunchDrawer', () => {
  beforeEach(() => {
    vi.spyOn(MESSAGING$, 'notifySuccess').mockImplementation(() => undefined);
    mocks.launchScenario.mockResolvedValue({ simulation_id: 'simulation-1' });
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('given_aTimeBasedScenario_should_launchWithTheTargetsAndOpenTheSimulationOverview', async () => {
    // Arrange
    render(<ScenarioLaunchDrawer scenario={scenario()} onClose={vi.fn()} />);

    // Act
    fireEvent.click(screen.getByTestId('submit'));

    // Assert
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/admin/simulations/simulation-1'));
    expect(screen.getByTestId('submit').getAttribute('data-with-targets')).toBe('true');
    expect(mocks.launchScenario).toHaveBeenCalledWith('scenario-1', {
      asset_ids: ['asset-1'],
      asset_group_ids: [],
      team_ids: [],
      manual_targets: ['10.0.0.1', 'app.example.com'],
    });
    expect(MESSAGING$.notifySuccess).toHaveBeenCalledWith('New simulation successfully created and started');
  });

  it('given_aChainedScenario_should_takeNoTargetsAndOpenTheAttackPath', async () => {
    // Arrange
    render(<ScenarioLaunchDrawer scenario={scenario('workflow-1')} onClose={vi.fn()} />);

    // Act
    fireEvent.click(screen.getByTestId('submit'));

    // Assert
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/admin/simulations/simulation-1/attack-path'));
    expect(screen.getByTestId('submit').getAttribute('data-with-targets')).toBe('false');
  });
});
