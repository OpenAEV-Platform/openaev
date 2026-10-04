import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import IocValidationDecisionActions from '../../../../admin/components/ioc_validations/IocValidationDecisionActions';
import IocValidationSkeleton from '../../../../admin/components/ioc_validations/IocValidationSkeleton';
import { type IocValidationOutput } from '../../../../utils/api-types';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';

vi.mock('../../../../components/i18n', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...(original as Record<string, unknown>),
    useFormatter: () => ({ t: (value: string, values?: Record<string, string>) => value.replace('{count}', values?.count ?? '{count}') }),
  };
});

vi.mock('../../../../actions/ioc_validations/ioc-validation-actions', () => ({
  approveIocValidation: vi.fn(() => Promise.resolve({ data: {} })),
  rejectIocValidation: vi.fn(() => Promise.resolve({ data: {} })),
}));

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

const renderActions = (iocValidation: IocValidationOutput) => render(
  <AbilityContext.Provider value={{ can: () => true } as unknown as AppAbility}>
    <IocValidationDecisionActions iocValidation={iocValidation} onUpdate={vi.fn()} onRefresh={vi.fn()} />
  </AbilityContext.Provider>,
);

describe('IocValidationDecisionActions', () => {
  afterEach(() => cleanup());

  it('shows next to the approval the tests that run and the security platforms expected to see them', () => {
    renderActions(awaitingRequest(2));
    fireEvent.click(screen.getByRole('button', { name: 'Approve and start the simulation' }));
    const summary = screen.getByTestId('ioc-validation-approval-summary');
    expect(within(summary).getAllByText('Tests that run once approved').length).toBeGreaterThan(0);
    expect(within(summary).getAllByText('evil-0.example').length).toBeGreaterThan(0);
    expect(within(summary).getAllByText('DNS resolution')).toHaveLength(2);
    // A skipped indicator runs no test, so it is not listed
    expect(within(summary).queryByText('evil-2.example')).toBeNull();
    expect(within(summary).getByText('Corporate EDR, SOC SIEM')).toBeTruthy();
    expect(within(summary).queryByText(/more indicators/)).toBeNull();
  });

  it('caps the tests listed in the approval and counts the others', () => {
    renderActions(awaitingRequest(13));
    fireEvent.click(screen.getByRole('button', { name: 'Approve and start the simulation' }));
    const summary = screen.getByTestId('ioc-validation-approval-summary');
    expect(within(summary).getAllByText('DNS resolution')).toHaveLength(10);
    expect(within(summary).getByText('3 more indicators')).toBeTruthy();
  });

  it('says so in the approval when the request names no security platform', () => {
    renderActions({
      ...awaitingRequest(1),
      ioc_validation_pairs: [],
    } as unknown as IocValidationOutput);
    fireEvent.click(screen.getByRole('button', { name: 'Approve and start the simulation' }));
    const summary = screen.getByTestId('ioc-validation-approval-summary');
    expect(within(summary).getByText('None named in the request')).toBeTruthy();
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
