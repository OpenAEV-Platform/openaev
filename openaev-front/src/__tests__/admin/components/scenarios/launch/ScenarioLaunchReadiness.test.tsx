import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type ScenarioLaunchCheckOutput } from '../../../../../utils/api-types';

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

const NO_PROBLEM: ScenarioLaunchCheckOutput = {
  launch_check_injects: [],
  launch_check_missing_requirements: [],
  launch_check_revoked_injects: [],
};

const checkFinds = (check: Partial<ScenarioLaunchCheckOutput>) => {
  mocks.fetchScenarioLaunchCheck.mockResolvedValue({
    ...NO_PROBLEM,
    ...check,
  });
  mocks.searchScenarioHealthcheks.mockResolvedValue({ data: [] });
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
    render(<ScenarioLaunchReadiness scenarioId="scenario-1" onOpen={onOpen} />);

    // Act
    fireEvent.click(await screen.findByRole('button', { name: '1 to fix before launch' }));

    // Assert
    expect(onOpen).toHaveBeenCalled();
  });

  it('given nothing to fix should show nothing', async () => {
    // Arrange
    checkFinds({});

    // Act
    render(<ScenarioLaunchReadiness scenarioId="scenario-1" onOpen={vi.fn()} />);

    // Assert
    await vi.waitFor(() => expect(mocks.fetchScenarioLaunchCheck).toHaveBeenCalledWith('scenario-1'));
    expect(screen.queryByTestId('scenario-launch-readiness')).toBeNull();
  });

  it('given the check closed should read it again', async () => {
    // Arrange
    checkFinds({});
    const { rerender } = render(<ScenarioLaunchReadiness scenarioId="scenario-1" onOpen={vi.fn()} checkOpen />);
    await vi.waitFor(() => expect(mocks.fetchScenarioLaunchCheck).toHaveBeenCalledTimes(1));

    // Act
    rerender(<ScenarioLaunchReadiness scenarioId="scenario-1" onOpen={vi.fn()} checkOpen={false} />);

    // Assert
    await vi.waitFor(() => expect(mocks.fetchScenarioLaunchCheck).toHaveBeenCalledTimes(2));
  });
});
