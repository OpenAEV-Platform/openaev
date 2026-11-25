import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { SCENARIO_TYPE_TIME_BASED } from '../../../../../admin/components/scenarios/scenario/ScenarioType';

const LAUNCH = 'Launch';
const CLOSE = 'Close the check';

const mocks = vi.hoisted(() => ({
  checkAgain: vi.fn(),
  drawerProps: vi.fn(),
}));

vi.mock('../../../../../actions/scenarios/scenario-actions', () => ({ launchScenario: vi.fn() }));
vi.mock('../../../../../admin/components/scenarios/launch/useOpenLaunchedSimulation', () => ({ default: () => vi.fn() }));
vi.mock('../../../../../admin/components/scenarios/launch/ScenarioLaunchCheckDrawer', () => ({
  default: (props: { onClose: () => void }) => {
    mocks.drawerProps(props);
    return <button type="button" onClick={props.onClose}>{CLOSE}</button>;
  },
}));

const { default: useScenarioLaunch } = await import('../../../../../admin/components/scenarios/launch/useScenarioLaunch');

const sharedCheck = {
  launchCheck: null,
  checking: false,
  checkAgain: mocks.checkAgain,
};

const Opener = () => {
  const { launch, launchDrawer } = useScenarioLaunch(undefined, sharedCheck);
  return (
    <>
      <button
        type="button"
        onClick={() => launch({
          scenarioId: 'scenario-1',
          name: 'Phishing',
          type: SCENARIO_TYPE_TIME_BASED,
        })}
      >
        {LAUNCH}
      </button>
      {launchDrawer}
    </>
  );
};

describe('useScenarioLaunch', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given a shared check should hand it to the drawer and read it again as the drawer opens and closes', () => {
    // Arrange
    render(<Opener />);

    // Act
    act(() => fireEvent.click(screen.getByRole('button', { name: LAUNCH })));

    // Assert: opened on the shared check, read again so what launches is current.
    expect(mocks.drawerProps).toHaveBeenLastCalledWith(expect.objectContaining({ sharedCheck }));
    expect(mocks.checkAgain).toHaveBeenCalledTimes(1);

    // Act
    act(() => fireEvent.click(screen.getByRole('button', { name: CLOSE })));

    // Assert: read again on close, so what was fixed there shows on the button.
    expect(mocks.checkAgain).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('button', { name: CLOSE })).toBeNull();
  });
});
