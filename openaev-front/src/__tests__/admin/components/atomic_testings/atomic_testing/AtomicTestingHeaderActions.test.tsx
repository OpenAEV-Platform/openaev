import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AtomicTestingHeaderActions from '../../../../../admin/components/atomic_testings/atomic_testing/AtomicTestingHeaderActions';
import { type InjectResultOverviewOutput, type PayloadSimple } from '../../../../../utils/api-types';

vi.mock('../../../../../components/i18n', async importOriginal => ({
  ...(await importOriginal<object>()),
  useFormatter: () => ({
    t: (value: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [key, val]) => text.replace(`{${key}}`, String(val)), value),
  }),
}));
vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));
vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../utils/hooks/useEnterpriseEdition', () => ({ default: () => ({ setEEFeatureDetectedInfo: vi.fn() }) }));
vi.mock('../../../../../actions/atomic_testings/atomic-testing-actions', () => ({
  fetchAtomicTestingExpectationsDrift: () => Promise.resolve({ data: null }),
  launchAtomicTesting: vi.fn(),
  relaunchAtomicTesting: vi.fn(),
  updateAtomicTestingRecurrence: vi.fn(),
}));
vi.mock('../../../../../admin/components/atomic_testings/atomic_testing/AtomicTestingPopover', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/atomic_testings/atomic_testing/AtomicTestingUpdate', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/reporting/EntityReportsPanel', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/common/injects/expectations/ExpectationsDriftIndicator', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/common/scheduling/SchedulingDialog', () => ({ default: () => null }));

// An atomic testing that already ran: its button is "Relaunch now".
const atomicTesting = (approvalStatus: PayloadSimple['payload_approval_status']) => ({
  inject_id: 'atomic-1',
  inject_title: 'TEST-ATOMIC',
  inject_ready: true,
  inject_status: {
    status_id: 'status-1',
    status_name: 'SUCCESS',
  },
  inject_injector_contract: {
    injector_contract_id: 'contract-1',
    injector_contract_payload: { payload_approval_status: approvalStatus },
  },
}) as unknown as InjectResultOverviewOutput;

const renderActions = (overview: InjectResultOverviewOutput) => render(
  <MemoryRouter>
    <ThemeProvider theme={createTheme()}>
      <TooltipProvider>
        <AtomicTestingHeaderActions injectResultOverview={overview} setInjectResultOverview={vi.fn()} />
      </TooltipProvider>
    </ThemeProvider>
  </MemoryRouter>,
);

// A non-approved action no longer disables the button: like a revoked payload, the server refuses
// the launch and the user sees why.
describe('AtomicTestingHeaderActions: relaunch with an action that is not approved', () => {
  afterEach(() => {
    cleanup();
  });

  it.each(['PENDING', 'REJECTED', 'SUPERSEDED', 'APPROVED'] as const)('keeps Relaunch now enabled for a %s version and opens the confirm dialog', (approvalStatus) => {
    // Arrange
    renderActions(atomicTesting(approvalStatus));

    // Act
    const button = screen.getByRole('button', { name: 'Relaunch now' }) as HTMLButtonElement;
    fireEvent.click(button);

    // Assert
    expect(button.disabled).toBe(false);
    expect(screen.queryByLabelText(/Can't launch/)).toBeNull();
    expect(screen.getByRole('dialog')).toBeTruthy();
  });
});
