import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { SCENARIO_TYPE_TIME_BASED } from '../../../../../admin/components/scenarios/scenario/ScenarioType';
import { NO_TARGETS } from '../../../../../admin/components/scenarios/targets/scenarioTargets';
import { type HealthCheck, type ScenarioLaunchCheckOutput } from '../../../../../utils/api-types';

const mocks = vi.hoisted(() => ({
  fetchScenarioLaunchCheck: vi.fn(),
  searchScenarioHealthcheks: vi.fn(),
}));

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [name, value]) => text.replace(`{${name}}`, String(value)), key),
  }),
}));
vi.mock('../../../../../actions/scenarios/scenario-actions', () => mocks);
vi.mock('../../../../../utils/permissions/useScenarioPermissions', () => ({ default: () => ({ canManage: true }) }));
vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));
vi.mock('../../../../../admin/components/scenarios/launch/RevokedInjectsFix', () => ({ default: () => null }));

const { default: ScenarioLaunchCheckDrawer } = await import('../../../../../admin/components/scenarios/launch/ScenarioLaunchCheckDrawer');

const NO_PROBLEM: ScenarioLaunchCheckOutput = {
  launch_check_injects: [],
  launch_check_missing_requirements: [],
  launch_check_revoked_injects: [],
};

const UNTARGETED = {
  inject_id: 'scan',
  inject_title: 'Port scan',
  inject_target_kinds: ['ASSETS' as const],
  inject_targeted: false,
};

const REVOKED = {
  inject_id: 'dump',
  inject_title: 'Credential dump',
  inject_action_id: 'dump-action',
  inject_action_name: 'Dump credentials',
};

const renderDrawer = (check: ScenarioLaunchCheckOutput, healthchecks: HealthCheck[] = [], onLaunch = vi.fn().mockResolvedValue(undefined)) => {
  mocks.fetchScenarioLaunchCheck.mockResolvedValue(check);
  mocks.searchScenarioHealthcheks.mockResolvedValue({ data: healthchecks });
  return render(
    <MemoryRouter>
      <ScenarioLaunchCheckDrawer
        scenario={{
          scenarioId: 'phishing',
          name: 'Phishing',
          type: SCENARIO_TYPE_TIME_BASED,
        }}
        onLaunch={onLaunch}
        onClose={vi.fn()}
      />
    </MemoryRouter>,
  );
};

describe('ScenarioLaunchCheckDrawer', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it.each<[string, ScenarioLaunchCheckOutput, HealthCheck[], string, string, boolean]>([
    ['nothingWrong', NO_PROBLEM, [], 'Ready to launch', 'Launch now', false],
    ['anInjectOnARevokedVersion', {
      ...NO_PROBLEM,
      launch_check_revoked_injects: [REVOKED],
    }, [], 'Ready to launch, but 1 check may make injects fail', 'Launch anyway', false],
    ['anInjectWithoutTarget', {
      ...NO_PROBLEM,
      launch_check_injects: [UNTARGETED],
    }, [], '1 thing to fix before this scenario can launch', 'Launch now', true],
    ['aMissingIntegrationAndAnEmptyScope', {
      ...NO_PROBLEM,
      launch_check_missing_requirements: [{
        requirement_label: 'Injector openaev_nmap',
        requirement_name: 'Nmap',
      }],
    }, [{
      type: 'SCOPE_DEFINITION',
      detail: 'EMPTY',
      status: 'ERROR',
      creation_date: '2026-11-21',
    }], '2 things to fix before this scenario can launch', 'Launch now', true],
  ])('given_%s_should_sumUpTheCheckAndGateTheLaunch', async (_case, check, healthchecks, summary, launchLabel, disabled) => {
    // Act
    renderDrawer(check, healthchecks);

    // Assert
    expect(await screen.findByText(summary)).toBeTruthy();
    expect((screen.getByRole('button', { name: launchLabel }) as HTMLButtonElement).disabled).toBe(disabled);
  });

  it('given_launchClicked_should_startWithoutExtraTargets', async () => {
    // Arrange
    const onLaunch = vi.fn().mockResolvedValue(undefined);
    renderDrawer(NO_PROBLEM, [], onLaunch);

    // Act
    fireEvent.click(await screen.findByRole('button', { name: 'Launch now' }));

    // Assert
    expect(onLaunch).toHaveBeenCalledWith(NO_TARGETS);
  });

  it('given_targetsSetForAnUntargetedInject_should_launchWithThem', async () => {
    // Arrange
    const onLaunch = vi.fn().mockResolvedValue(undefined);
    renderDrawer({
      ...NO_PROBLEM,
      launch_check_injects: [{
        ...UNTARGETED,
        inject_target_kinds: ['HOSTS'],
      }],
    }, [], onLaunch);
    fireEvent.click(await screen.findByRole('button', { name: 'Set targets' }));

    // Act
    fireEvent.change(screen.getByRole('textbox', { name: 'Manual targets' }), { target: { value: '10.0.0.1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Launch' }));

    // Assert
    expect(onLaunch).toHaveBeenCalledWith({
      ...NO_TARGETS,
      manual: '10.0.0.1',
    });
  });

  it('given_aSharedCheck_should_showItWithoutLoadingItsOwn', async () => {
    // Arrange
    const checkAgain = vi.fn();

    // Act
    render(
      <MemoryRouter>
        <ScenarioLaunchCheckDrawer
          scenario={{
            scenarioId: 'phishing',
            name: 'Phishing',
            type: SCENARIO_TYPE_TIME_BASED,
          }}
          onLaunch={vi.fn()}
          onClose={vi.fn()}
          sharedCheck={{
            launchCheck: {
              check: NO_PROBLEM,
              untargeted: [],
              revokedByAction: [],
              healthchecks: [],
              scopeBlocked: false,
              blocking: 0,
              failing: 0,
              onlyRevokedInjectsFail: false,
            },
            checking: false,
            checkAgain,
          }}
        />
      </MemoryRouter>,
    );

    // Assert
    expect(await screen.findByText('Ready to launch')).toBeTruthy();
    expect(mocks.fetchScenarioLaunchCheck).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Check again' }));
    expect(checkAgain).toHaveBeenCalled();
  });
});
