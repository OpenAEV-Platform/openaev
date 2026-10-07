import { TooltipProvider } from '@filigran/design-system';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { approveIocValidation, fetchIocValidationApprovalPreview } from '../../../../actions/ioc_validations/ioc-validation-actions';
import IocValidationDecisionActions from '../../../../admin/components/ioc_validations/IocValidationDecisionActions';
import IocValidationSkeleton from '../../../../admin/components/ioc_validations/IocValidationSkeleton';
import { type IocValidationApprovalPreviewOutput, type IocValidationOutput } from '../../../../utils/api-types';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { AbilityProvider } from '../../../../utils/permissions/permissionsContext';

vi.mock('../../../../components/i18n', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...(original as Record<string, unknown>),
    useFormatter: () => ({ t: (value: string, values?: Record<string, string>) => value.replace('{count}', values?.count ?? '{count}') }),
  };
});

vi.mock('../../../../components/Empty', () => vi.importActual('../../../../components/Empty.tsx'));

vi.mock('../../../../actions/ioc_validations/ioc-validation-actions', () => ({
  approveIocValidation: vi.fn(() => Promise.resolve({ data: {} })),
  fetchIocValidationApprovalPreview: vi.fn(),
  rejectIocValidation: vi.fn(() => Promise.resolve({ data: {} })),
}));

const FINGERPRINT = 'f'.repeat(64);

const ioc = (index: number, testKind: string | null) => ({
  ioc_indicator_ref: `indicator--${index}`,
  ioc_indicator_name: `evil-${index}.example`,
  ioc_observable_type: 'Domain-Name',
  ioc_value: `evil-${index}.example`,
  ioc_requested_test_kind: 'DNS_RESOLUTION',
  ioc_test_kind: testKind,
});

const awaitingRequest = (plannedCount: number) => ({
  ioc_validation_id: 'request-1',
  ioc_validation_status: 'AWAITING_APPROVAL',
  ioc_validation_iocs: [
    ...Array.from({ length: plannedCount }, (_, index) => ioc(index, 'DNS_RESOLUTION')),
    ioc(plannedCount, null),
  ],
  ioc_validation_pairs: [
    {
      pair_indicator_ref: 'indicator--0',
      pair_platform_ref: 'identity--edr',
      pair_platform_name: 'Corporate EDR',
    },
    {
      pair_indicator_ref: 'indicator--0',
      pair_platform_ref: 'identity--siem',
      pair_platform_name: 'SOC SIEM',
    },
    {
      pair_indicator_ref: 'indicator--1',
      pair_platform_ref: 'identity--edr',
      pair_platform_name: 'Corporate EDR',
    },
    // Only the skipped indicator is paired with this platform: no test is expected there
    {
      pair_indicator_ref: `indicator--${plannedCount}`,
      pair_platform_ref: 'identity--ndr',
      pair_platform_name: 'Network NDR',
    },
  ],
}) as unknown as IocValidationOutput;

// The preview of the server when nothing changed since the intake: the plan of the request, the pairs of the tests that run
const previewOf = (request: IocValidationOutput, changes: Partial<IocValidationApprovalPreviewOutput> = {}) => {
  const planned = new Set(request.ioc_validation_iocs.filter(item => item.ioc_test_kind).map(item => item.ioc_indicator_ref));
  return {
    ioc_validation_preview_fingerprint: FINGERPRINT,
    ioc_validation_preview_iocs: request.ioc_validation_iocs,
    ioc_validation_preview_pairs: request.ioc_validation_pairs.filter(pair => planned.has(pair.pair_indicator_ref)),
    ...changes,
  } as IocValidationApprovalPreviewOutput;
};

const renderActions = (iocValidation: IocValidationOutput) => render(
  <TooltipProvider>
    <AbilityProvider value={{
      can: () => true,
      on: () => () => {},
    } as unknown as AppAbility}
    >
      <IocValidationDecisionActions iocValidation={iocValidation} onUpdate={vi.fn()} onRefresh={vi.fn()} />
    </AbilityProvider>
  </TooltipProvider>,
);

const openApproval = (iocValidation: IocValidationOutput, preview: IocValidationApprovalPreviewOutput) => {
  vi.mocked(fetchIocValidationApprovalPreview).mockResolvedValue({ data: preview } as never);
  renderActions(iocValidation);
  fireEvent.click(screen.getByRole('button', { name: 'Approve and start the simulation' }));
};

const confirmButton = () => within(screen.getByRole('dialog')).getByRole('button', { name: 'Approve and start the simulation' });

const approvalQuestion = () => within(screen.getByRole('dialog')).queryByText(/^Approve this IOC validation\?/);

describe('IocValidationDecisionActions', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('shows next to the approval the tests that run and the security platforms expected to see them', async () => {
    const request = awaitingRequest(2);
    openApproval(request, previewOf(request));
    const summary = await screen.findByTestId('ioc-validation-approval-summary');
    await within(summary).findByText('Corporate EDR, SOC SIEM');
    expect(fetchIocValidationApprovalPreview).toHaveBeenCalledWith('request-1');
    expect(within(summary).getAllByText('Tests that run once approved').length).toBeGreaterThan(0);
    expect(within(summary).getAllByText('evil-0.example').length).toBeGreaterThan(0);
    expect(within(summary).getAllByText('DNS resolution')).toHaveLength(2);
    // A skipped indicator runs no test, so it is not listed
    expect(within(summary).queryByText('evil-2.example')).toBeNull();
    expect(within(summary).queryByText(/more indicators/)).toBeNull();
  });

  it('shows what the approval would start now, not the plan recorded when the request arrived', async () => {
    // A setting narrowed since the intake drops the test of the first indicator
    const request = awaitingRequest(2);
    const preview = previewOf(request, {
      ioc_validation_preview_iocs: [ioc(0, null), ioc(1, 'DNS_RESOLUTION'), ioc(2, null)] as IocValidationApprovalPreviewOutput['ioc_validation_preview_iocs'],
      ioc_validation_preview_pairs: request.ioc_validation_pairs.filter(pair => pair.pair_indicator_ref === 'indicator--1'),
    });
    openApproval(request, preview);
    const summary = await screen.findByTestId('ioc-validation-approval-summary');
    await within(summary).findByText('Corporate EDR');
    expect(within(summary).getAllByText('DNS resolution')).toHaveLength(1);
    expect(within(summary).queryByText('evil-0.example')).toBeNull();
    expect(within(summary).queryByText(/SOC SIEM/)).toBeNull();
  });

  it('approves with the fingerprint of the preview shown', async () => {
    const request = awaitingRequest(1);
    openApproval(request, previewOf(request));
    await waitFor(() => expect(confirmButton().hasAttribute('disabled')).toBe(false));
    fireEvent.click(confirmButton());
    await waitFor(() => expect(approveIocValidation).toHaveBeenCalledWith('request-1', { ioc_validation_preview_fingerprint: FINGERPRINT }));
  });

  const openPendingApproval = (iocValidation: IocValidationOutput) => {
    vi.mocked(fetchIocValidationApprovalPreview).mockReturnValue(new Promise(() => {}) as never);
    renderActions(iocValidation);
    fireEvent.click(screen.getByRole('button', { name: 'Approve and start the simulation' }));
    return screen.getByTestId('ioc-validation-approval-summary');
  };

  it('keeps the place of the planned tests and the approval disabled while the preview loads', () => {
    const summary = openPendingApproval(awaitingRequest(3));
    expect(summary.getAttribute('aria-busy')).toBe('true');
    expect(within(summary).getByRole('status').textContent).toBe('Checking the tests with the current settings...');
    // A header and one placeholder row per test the request shows as planned, then the security platforms field
    expect(within(summary).getAllByRole('row')).toHaveLength(4);
    expect(within(summary).getByText('Test that runs')).toBeTruthy();
    expect(within(summary).getByText('Security platforms')).toBeTruthy();
    expect(approvalQuestion()).toBeTruthy();
    expect(confirmButton().hasAttribute('disabled')).toBe(true);
  });

  it('keeps as many placeholder rows as the approval lists while the preview loads', () => {
    const summary = openPendingApproval(awaitingRequest(13));
    expect(within(summary).getAllByRole('row')).toHaveLength(11);
    expect(within(summary).getByText('3 more indicators')).toBeTruthy();
  });

  it('keeps every row of the summary at one height on one line, while the preview loads and once it lands', async () => {
    const request = awaitingRequest(2);
    let land: (value: unknown) => void = () => {};
    vi.mocked(fetchIocValidationApprovalPreview).mockReturnValue(new Promise((resolve) => {
      land = resolve;
    }) as never);
    renderActions(request);
    fireEvent.click(screen.getByRole('button', { name: 'Approve and start the simulation' }));
    const summary = () => screen.getByTestId('ioc-validation-approval-summary');
    const bodyRowHeights = () => within(summary()).getAllByRole('row').slice(1).map(row => row.style.height);
    const loading = bodyRowHeights();
    land({ data: previewOf(request) });
    await waitFor(() => expect(summary().getAttribute('aria-busy')).toBeNull());
    expect(loading).toHaveLength(2);
    expect(loading[0]).not.toBe('');
    expect(bodyRowHeights()).toEqual(loading);
    // A value longer than its column ellipses instead of wrapping
    const cells = within(summary()).getAllByRole('cell');
    expect(cells).toHaveLength(6);
    cells.forEach(cell => expect((cell.firstElementChild as HTMLElement).style.whiteSpace).toBe('nowrap'));
  });

  it('shows at once the warning of a request without planned test, while its preview loads', () => {
    const summary = openPendingApproval(awaitingRequest(0));
    expect(within(summary).getByText(/^Nothing can run: every IOC of this request was skipped when it was received\./)).toBeTruthy();
    expect(within(summary).queryByRole('table')).toBeNull();
    expect(within(summary).queryByText('Security platforms')).toBeNull();
    // Nothing can be approved: no question above the warning
    expect(approvalQuestion()).toBeNull();
    expect(confirmButton().hasAttribute('disabled')).toBe(true);
  });

  it('keeps the same warning once the preview of a request without planned test lands', async () => {
    const request = awaitingRequest(0);
    openApproval(request, previewOf(request, { ioc_validation_preview_blocker: 'Nothing can run: every IOC of this request was skipped when it was received or is no longer allowed by the IOC validation settings.' }));
    const summary = await screen.findByTestId('ioc-validation-approval-summary');
    await waitFor(() => expect(summary.getAttribute('aria-busy')).toBe('false'));
    expect(within(summary).getAllByText(/^Nothing can run:/)).toHaveLength(1);
    expect(within(summary).getByText(/skipped when it was received\. Reject the request/)).toBeTruthy();
    expect(approvalQuestion()).toBeNull();
    expect(confirmButton().hasAttribute('disabled')).toBe(true);
  });

  it('shows only the warning when nothing can run, and keeps the approval disabled', async () => {
    const request = awaitingRequest(1);
    const blocker = 'Nothing can run: every IOC of this request was skipped when it was received or is no longer allowed by the IOC validation settings.';
    openApproval(request, previewOf(request, {
      ioc_validation_preview_iocs: [ioc(0, null), ioc(1, null)] as IocValidationApprovalPreviewOutput['ioc_validation_preview_iocs'],
      ioc_validation_preview_pairs: [],
      ioc_validation_preview_blocker: blocker,
    }));
    const summary = await screen.findByTestId('ioc-validation-approval-summary');
    expect(await within(summary).findByText(blocker)).toBeTruthy();
    expect(within(summary).queryByText('Tests that run once approved')).toBeNull();
    expect(within(summary).queryByText('No test would run now.')).toBeNull();
    expect(within(summary).queryByText('Security platforms')).toBeNull();
    expect(approvalQuestion()).toBeNull();
    expect(confirmButton().hasAttribute('disabled')).toBe(true);
    expect(approveIocValidation).not.toHaveBeenCalled();
  });

  it('keeps the planned tests under the warning when their targets cannot run them', async () => {
    const request = awaitingRequest(2);
    const blocker = 'No endpoint of the asset group \'Validation targets\' has an active agent: start an agent or choose another asset group in Settings > Customization > IOC validation, then approve again.';
    openApproval(request, previewOf(request, { ioc_validation_preview_blocker: blocker }));
    const summary = await screen.findByTestId('ioc-validation-approval-summary');
    expect(await within(summary).findByText(blocker)).toBeTruthy();
    expect(within(summary).getAllByText('DNS resolution')).toHaveLength(2);
    expect(within(summary).getByText('Corporate EDR, SOC SIEM')).toBeTruthy();
    expect(approvalQuestion()).toBeTruthy();
    expect(confirmButton().hasAttribute('disabled')).toBe(true);
  });

  it('caps the tests listed in the approval and counts the others', async () => {
    const request = awaitingRequest(13);
    openApproval(request, previewOf(request));
    const summary = await screen.findByTestId('ioc-validation-approval-summary');
    await within(summary).findByText('3 more indicators');
    expect(within(summary).getAllByText('DNS resolution')).toHaveLength(10);
  });

  it('shows the empty-value placeholder when no security platform is paired with the tests that run', async () => {
    // The intake refuses such a request: only a validation recorded before that check can show it
    const request = {
      ...awaitingRequest(1),
      ioc_validation_pairs: [],
    } as unknown as IocValidationOutput;
    openApproval(request, previewOf(request));
    const summary = await screen.findByTestId('ioc-validation-approval-summary');
    expect(await within(summary).findByText('-')).toBeTruthy();
  });

  it('offers no decision once the request is decided', () => {
    renderActions({
      ...awaitingRequest(1),
      ioc_validation_status: 'RUNNING',
    } as IocValidationOutput);
    expect(screen.queryByRole('button', { name: 'Approve and start the simulation' })).toBeNull();
  });
});

describe('IocValidationSkeleton', () => {
  afterEach(() => cleanup());

  it('keeps the sections of the loaded page while the request loads', () => {
    render(<IocValidationSkeleton />);
    const skeleton = screen.getByTestId('ioc-validation-skeleton');
    expect(skeleton.getAttribute('aria-busy')).toBe('true');
    ['Request', 'Results', 'Tested IOCs', 'Security platforms'].forEach(title => expect(within(skeleton).getByText(title)).toBeTruthy());
  });
});
