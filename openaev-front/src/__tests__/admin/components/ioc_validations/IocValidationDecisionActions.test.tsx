import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { approveIocValidation, fetchIocValidationApprovalPreview } from '../../../../actions/ioc_validations/ioc-validation-actions';
import IocValidationDecisionActions from '../../../../admin/components/ioc_validations/IocValidationDecisionActions';
import IocValidationSkeleton from '../../../../admin/components/ioc_validations/IocValidationSkeleton';
import { type IocValidationApprovalPreviewOutput, type IocValidationOutput } from '../../../../utils/api-types';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';

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
  <AbilityContext.Provider value={{ can: () => true } as unknown as AppAbility}>
    <IocValidationDecisionActions iocValidation={iocValidation} onUpdate={vi.fn()} onRefresh={vi.fn()} />
  </AbilityContext.Provider>,
);

const openApproval = (iocValidation: IocValidationOutput, preview: IocValidationApprovalPreviewOutput) => {
  vi.mocked(fetchIocValidationApprovalPreview).mockResolvedValue({ data: preview } as never);
  renderActions(iocValidation);
  fireEvent.click(screen.getByRole('button', { name: 'Approve and start the simulation' }));
};

const confirmButton = () => within(screen.getByRole('dialog')).getByRole('button', { name: 'Approve and start the simulation' });

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

  it('keeps the approval disabled while the preview loads', () => {
    vi.mocked(fetchIocValidationApprovalPreview).mockReturnValue(new Promise(() => {}) as never);
    renderActions(awaitingRequest(1));
    fireEvent.click(screen.getByRole('button', { name: 'Approve and start the simulation' }));
    expect(screen.getByTestId('ioc-validation-approval-summary').getAttribute('aria-busy')).toBe('true');
    expect(screen.getByText('Checking the tests with the current settings...')).toBeTruthy();
    expect(confirmButton().hasAttribute('disabled')).toBe(true);
  });

  it('says why nothing would run and keeps the approval disabled', async () => {
    const request = awaitingRequest(1);
    const blocker = 'Nothing can run: every IOC of this request was skipped when it was received or is no longer allowed by the IOC validation settings.';
    openApproval(request, previewOf(request, {
      ioc_validation_preview_iocs: [ioc(0, null), ioc(1, null)] as IocValidationApprovalPreviewOutput['ioc_validation_preview_iocs'],
      ioc_validation_preview_pairs: [],
      ioc_validation_preview_blocker: blocker,
    }));
    expect(await screen.findByText(blocker)).toBeTruthy();
    expect(screen.getByText('No test would run now.')).toBeTruthy();
    expect(confirmButton().hasAttribute('disabled')).toBe(true);
    expect(approveIocValidation).not.toHaveBeenCalled();
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
