import { TooltipProvider } from '@filigran/design-system';
import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import IocValidation from '../../../../admin/components/ioc_validations/IocValidation';
import { IOC_VALIDATION_FOCUS_RING_CLASS, IOC_VALIDATION_SETTINGS_URL } from '../../../../admin/components/ioc_validations/iocValidationUtils';
import { type IocValidationOutput } from '../../../../utils/api-types';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { AbilityProvider } from '../../../../utils/permissions/permissionsContext';
import { SUBJECTS } from '../../../../utils/permissions/types';

const hook = vi.hoisted(() => ({
  iocValidation: undefined as unknown,
  load: () => {},
  update: () => {},
}));

vi.mock('../../../../components/i18n', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...(original as Record<string, unknown>),
    useFormatter: () => ({
      t: (value: string) => value,
      fldt: (value: string) => value,
    }),
  };
});

// Vitest resolves the tracked, empty Empty.jsx before Empty.tsx; the application resolves .tsx first
vi.mock('../../../../components/Empty', () => vi.importActual('../../../../components/Empty.tsx'));

vi.mock('../../../../admin/components/ioc_validations/useIocValidation', () => ({
  default: () => ({
    iocValidation: hook.iocValidation,
    loadError: null,
    load: hook.load,
    update: hook.update,
  }),
}));

vi.mock('../../../../actions/ioc_validations/ioc-validation-actions', () => ({
  approveIocValidation: vi.fn(() => Promise.resolve({ data: {} })),
  fetchIocValidationApprovalPreview: vi.fn(() => new Promise(() => {})),
  rejectIocValidation: vi.fn(() => Promise.resolve({ data: {} })),
}));

const SETTINGS_SKIP = 'Not run: the IOC validation settings of this tenant do not allow this test (Network traffic)';

const request = (status: string) => ({
  ioc_validation_id: 'request-1',
  ioc_validation_name: 'Validate live deployments',
  ioc_validation_status: status,
  ioc_validation_requested_by: 'Jane Doe (OpenCTI)',
  ioc_validation_created_at: '2026-10-05T20:00:00Z',
  ioc_validation_requested_test_kinds: ['DNS_RESOLUTION', 'NETWORK_TRAFFIC'],
  ioc_validation_allowed_test_kinds: ['DNS_RESOLUTION'],
  ioc_validation_iocs: [
    {
      ioc_indicator_ref: 'indicator--1',
      ioc_indicator_name: 'evil.example',
      ioc_observable_type: 'Domain-Name',
      ioc_value: 'evil.example',
      ioc_requested_test_kind: 'DNS_RESOLUTION',
      ioc_test_kind: 'DNS_RESOLUTION',
    },
    {
      ioc_indicator_ref: 'indicator--2',
      ioc_indicator_name: '203.0.113.7',
      ioc_observable_type: 'IPv4-Addr',
      ioc_value: '203.0.113.7',
      ioc_requested_test_kind: 'NETWORK_TRAFFIC',
      ioc_test_kind: null,
      ioc_message: SETTINGS_SKIP,
    },
  ],
  ioc_validation_pairs: [
    {
      pair_indicator_ref: 'indicator--1',
      pair_platform_ref: 'identity--edr',
      pair_platform_name: 'Corporate EDR',
      pair_deployed_on_ref: 'relationship--1',
    },
  ],
}) as unknown as IocValidationOutput;

const renderDetail = (iocValidation: IocValidationOutput, can: boolean | ((action: string, subject: string) => boolean) = true) => {
  hook.iocValidation = iocValidation;
  const check = typeof can === 'function' ? can : () => can;
  return render(
    <AbilityProvider value={{
      can: check,
      on: () => () => {},
    } as unknown as AppAbility}
    >
      <TooltipProvider>
        <MemoryRouter>
          <IocValidation />
        </MemoryRouter>
      </TooltipProvider>
    </AbilityProvider>,
  );
};

describe('IocValidation', () => {
  afterEach(() => cleanup());

  it('centres the empty results of a request awaiting approval in their card', () => {
    renderDetail(request('AWAITING_APPROVAL'));
    const message = screen.getByText('Results appear once the simulation runs');
    const card = message.closest('[data-testid="section-block-paper"]') as HTMLElement;
    expect(card).not.toBeNull();
    expect(card.style.display).toBe('flex');
    expect(card.style.alignItems).toBe('center');
  });

  it('keeps an IOC skipped by the safety settings on one line, its full reason in a tooltip', () => {
    renderDetail(request('AWAITING_APPROVAL'));
    const table = screen.getByRole('table', { name: 'Tested IOCs' });
    const reason = within(table).getByText('Not allowed by the safety settings');
    expect(reason.style.whiteSpace).toBe('nowrap');
    expect(reason.getAttribute('tabindex')).toBe('0');
    expect(within(table).queryByText(SETTINGS_SKIP)).toBeNull();
    const link = within(table).getByRole('link', { name: 'Open settings' });
    expect(link.getAttribute('href')).toBe(IOC_VALIDATION_SETTINGS_URL);
    expect(reason.parentElement).toBe(link.parentElement);
  });

  it('gives the focusable reason, settings link and relative date the focus ring of the design system', () => {
    const { container } = renderDetail(request('AWAITING_APPROVAL'));
    const table = screen.getByRole('table', { name: 'Tested IOCs' });
    const date = container.querySelector('time') as HTMLElement;
    expect(date.getAttribute('tabindex')).toBe('0');
    [
      within(table).getByText('Not allowed by the safety settings'),
      within(table).getByRole('link', { name: 'Open settings' }),
      date,
    ].forEach((element) => {
      expect(element.className.split(' ')).toEqual(expect.arrayContaining(IOC_VALIDATION_FOCUS_RING_CLASS.split(' ')));
    });
  });

  it('offers the settings link only to users who can change the settings', () => {
    renderDetail(request('AWAITING_APPROVAL'), false);
    const table = screen.getByRole('table', { name: 'Tested IOCs' });
    expect(within(table).getByText('Not allowed by the safety settings')).toBeTruthy();
    expect(within(table).queryByRole('link', { name: 'Open settings' })).toBeNull();
  });

  it('links a security platform only for users who can open the security platforms', () => {
    const base = request('RUNNING');
    const withPlatform = {
      ...base,
      ioc_validation_pairs: base.ioc_validation_pairs.map(pair => ({
        ...pair,
        pair_security_platform_id: 'platform-1',
      })),
    } as IocValidationOutput;

    renderDetail(withPlatform);
    const link = within(screen.getByRole('table', { name: 'Security platforms' })).getByRole('link', { name: 'Corporate EDR' });
    expect(link.getAttribute('href')).toBe('/admin/security_platforms/platform-1');
    cleanup();

    renderDetail(withPlatform, (_action, subject) => subject !== SUBJECTS.SECURITY_PLATFORMS);
    const table = screen.getByRole('table', { name: 'Security platforms' });
    expect(within(table).queryByRole('link', { name: 'Corporate EDR' })).toBeNull();
    expect(within(table).getByText('Corporate EDR')).toBeTruthy();
  });

  it('shows the empty-value placeholder in the details and evaluated cells of a pending outcome', () => {
    renderDetail(request('RUNNING'));
    const table = screen.getByRole('table', { name: 'Security platforms' });
    const cells = within(within(table).getAllByRole('row')[1]).getAllByRole('cell');
    expect(cells[3].textContent).toBe('-');
    expect(cells[4].textContent).toBe('-');
  });
});
