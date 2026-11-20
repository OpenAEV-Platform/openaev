import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { NO_TARGETS } from '../../../../../admin/components/scenarios/targets/scenarioTargets';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock('../../../../../components/common/Drawer', () => ({ default: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock('../../../../../admin/components/scenarios/targets/ScenarioTargetsFields', () => ({ default: () => <div data-testid="targets" /> }));

const { default: ScenarioTargetsDrawer } = await import('../../../../../admin/components/scenarios/targets/ScenarioTargetsDrawer');

describe('ScenarioTargetsDrawer', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it.each([
    [true, 1],
    [false, 0],
  ])('given_withTargets_%s_should_show_%i_targetFieldsAndSubmitTheTargets', (withTargets, fields) => {
    // Arrange
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ScenarioTargetsDrawer
        title="Launch"
        description="Pick the targets"
        withTargets={withTargets}
        submitLabel="Launch now"
        onSubmit={onSubmit}
        onClose={vi.fn()}
      />,
    );

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Launch now' }));

    // Assert
    expect(screen.queryAllByTestId('targets')).toHaveLength(fields);
    expect(onSubmit).toHaveBeenCalledWith(NO_TARGETS);
  });
});
