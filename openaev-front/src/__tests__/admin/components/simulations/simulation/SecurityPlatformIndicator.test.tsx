import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  dispatch: vi.fn(),
  can: vi.fn(),
}));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => mocks.dispatch }));

vi.mock('../../../../../utils/hooks/useDataLoader', () => ({
  default: (loader: () => void) => {
    loader();
  },
}));

vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: mocks.can }) }));

vi.mock('../../../../../actions/assets/securityPlatform-actions', () => ({ fetchSecurityPlatforms: () => ({ type: 'fetchSecurityPlatforms' }) }));

vi.mock('../../../../../actions/chaining/workflow-actions', () => ({ fetchWorkflowConfiguration: () => ({ type: 'fetchWorkflowConfiguration' }) }));

// A launched run whose frozen snapshot holds one platform: it must stay visible without the capability.
vi.mock('../../../../../store', () => ({
  useHelper: (selector: (helper: unknown) => unknown) => selector({
    getSecurityPlatforms: () => [],
    getWorkflowConfiguration: () => ({
      workflow_security_platforms: [{
        security_platform_snapshot_id: 'platform-1',
        security_platform_snapshot_name: 'Platform 1',
        security_platform_snapshot_status: 'RESOLVED',
      }],
    }),
  }),
}));

import SecurityPlatformIndicator from '../../../../../admin/components/simulations/simulation/SecurityPlatformIndicator';

describe('SecurityPlatformIndicator (#9813)', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it.each([true, false])('loads the security platforms only when the user can access them (can: %s)', (canAccess) => {
    // Arrange
    mocks.can.mockReturnValue(canAccess);

    // Act
    const { container } = render(
      <ThemeProvider theme={createTheme()}>
        <TooltipProvider>
          <SecurityPlatformIndicator workflowId="workflow-1" launched />
        </TooltipProvider>
      </ThemeProvider>,
    );

    // Assert
    expect(mocks.dispatch.mock.calls.some(([action]) => action?.type === 'fetchSecurityPlatforms')).toBe(canAccess);
    expect(container.querySelector('svg')).not.toBeNull();
  });
});
