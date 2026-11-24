import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

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

const { default: ScenarioLaunchReadiness } = await import('../../../../../admin/components/scenarios/launch/ScenarioLaunchReadiness');
const { default: useLaunchCheck } = await import('../../../../../admin/components/scenarios/launch/useLaunchCheck');

// The button as the header shows it: on the check the header loads.
const Readiness = ({ onOpen = vi.fn() }: { onOpen?: () => void }) => {
  const { launchCheck } = useLaunchCheck('scenario-1');
  return <ScenarioLaunchReadiness launchCheck={launchCheck} onOpen={onOpen} />;
};

const NO_PROBLEM: ScenarioLaunchCheckOutput = {
  launch_check_injects: [],
  launch_check_missing_requirements: [],
  launch_check_revoked_injects: [],
};

const REVOKED_INJECTS: ScenarioLaunchCheckOutput['launch_check_revoked_injects'] = [
  {
    inject_id: 'dump',
    inject_title: 'Credential dump',
    inject_action_id: 'dump-action',
  },
  {
    inject_id: 'scan',
    inject_title: 'Port scan',
    inject_action_id: 'dump-action',
  },
];

const checkFinds = (check: Partial<ScenarioLaunchCheckOutput>, healthchecks: HealthCheck[] = []) => {
  mocks.fetchScenarioLaunchCheck.mockResolvedValue({
    ...NO_PROBLEM,
    ...check,
  });
  mocks.searchScenarioHealthcheks.mockResolvedValue({ data: healthchecks });
};

describe('ScenarioLaunchReadiness', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given something that blocks the launch should say how many to fix and open the check', async () => {
    // Arrange
    checkFinds({
      launch_check_injects: [{
        inject_id: 'scan',
        inject_title: 'Port scan',
        inject_target_kinds: ['ASSETS'],
        inject_targeted: false,
      }],
    });
    const onOpen = vi.fn();
    render(<Readiness onOpen={onOpen} />);

    // Act
    fireEvent.click(await screen.findByRole('button', { name: '1 to fix before launch' }));

    // Assert
    expect(onOpen).toHaveBeenCalled();
  });

  it('given only injects on a revoked version should say how many will fail', async () => {
    // Arrange
    checkFinds({ launch_check_revoked_injects: REVOKED_INJECTS });

    // Act
    render(<Readiness />);

    // Assert
    expect(await screen.findByRole('button', { name: '2 injects will fail' })).toBeTruthy();
  });

  it('given revoked injects and another warning should count the checks instead', async () => {
    // Arrange
    checkFinds({ launch_check_revoked_injects: REVOKED_INJECTS }, [{
      type: 'SCOPE_DEFINITION',
      detail: 'INEFFECTIVE_TECHNICAL_TARGETS',
      status: 'WARNING',
      creation_date: '2026-11-24',
    }]);

    // Act
    render(<Readiness />);

    // Assert
    expect(await screen.findByRole('button', { name: '2 to check before launch' })).toBeTruthy();
  });

  it('given nothing to fix should show nothing', async () => {
    // Arrange
    checkFinds({});

    // Act
    render(<Readiness />);

    // Assert
    await vi.waitFor(() => expect(mocks.fetchScenarioLaunchCheck).toHaveBeenCalledWith('scenario-1'));
    expect(screen.queryByTestId('scenario-launch-readiness')).toBeNull();
  });
});
