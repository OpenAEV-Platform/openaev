import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type CounterpartAnswer } from '../../../components/xtm/counterpartStore';
import XtmCounterpartsEmblem, { XtmCounterpartsEmblemView } from '../../../components/xtm/XtmCounterpartsEmblem';
import { safeCounterpartUrl, visibleSections, XTM_PRODUCTS } from '../../../components/xtm/xtmCounterpartsModel';

const can = vi.fn();
const useCounterpartMock = vi.fn();
vi.mock('../../../components/xtm/useCounterpart', () => ({
  default: (...args: unknown[]) => useCounterpartMock(...args),
  OPENCTI_PRODUCT: 'opencti',
}));
vi.mock('../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (s: string) => s,
    n: (v: number) => `${v}`,
    fsd: (d: string) => d.slice(0, 10),
  }),
}));
vi.mock('../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can }) }));
vi.mock('../../../actions/xtm/counterpart-actions', () => ({
  fetchXtmConnections: vi.fn(),
  lookupCounterparts: vi.fn(),
}));

const renderView = (element: ReactElement) => render(
  <ThemeProvider theme={createTheme()}><TooltipProvider>{element}</TooltipProvider></ThemeProvider>,
);

const def = (product: string) => XTM_PRODUCTS.find(p => p.product === product)!;
const answer = (product: string, state: CounterpartAnswer['state'], url?: string): CounterpartAnswer => ({
  state,
  product,
  counterpart: state === 'match'
    ? {
        counterpart_product: product,
        counterpart_status: 'match',
        counterpart_url: url,
        counterpart_name: 'web-02',
        counterpart_entity_type: 'Infrastructure',
      }
    : undefined,
});

describe('XtmCounterpartsEmblem (single XTM One emblem, parity with opencti #20153)', () => {
  beforeEach(() => {
    can.mockReturnValue(true);
    useCounterpartMock.mockReset();
  });
  afterEach(cleanup);

  it('lists the products in the fixed order XTM One, OpenCTI, OpenCRQ', () => {
    expect(XTM_PRODUCTS.map(p => p.product)).toEqual(['xtmone', 'opencti', 'opencrq']);
  });

  it('given a found product should draw one emblem, purple, and a surface with its open button', () => {
    const sections = visibleSections([
      {
        def: def('opencti'),
        answer: answer('opencti', 'match', 'https://octi.example/x/1'),
      },
      {
        def: def('opencrq'),
        answer: answer('opencrq', 'no_match'),
      },
    ], true);
    renderView(<XtmCounterpartsEmblemView sections={sections} />);
    const emblem = screen.getByTestId('xtm-emblem');
    expect(emblem.getAttribute('data-found')).toBe('1');
    expect(emblem.getAttribute('aria-label')).toBe('Across the XTM Platform');
    fireEvent.click(emblem);
    const surface = screen.getByTestId('xtm-surface');
    const octi = within(surface).getByTestId('opencti-section');
    expect(octi.getAttribute('data-status')).toBe('found');
    expect(within(octi).getByTestId('opencti-open').getAttribute('href')).toBe('https://octi.example/x/1');
    const crq = within(surface).getByTestId('opencrq-section');
    expect(crq.getAttribute('data-status')).toBe('not_found');
    expect(within(surface).getAllByTestId('xtm-surface-separator')).toHaveLength(1);
  });

  it('given nothing found should draw the grey emblem with the not found label', () => {
    const sections = visibleSections([{
      def: def('opencti'),
      answer: answer('opencti', 'no_match'),
    }], true);
    renderView(<XtmCounterpartsEmblemView sections={sections} />);
    const emblem = screen.getByTestId('xtm-emblem');
    expect(emblem.getAttribute('data-found')).toBe('0');
    expect(emblem.getAttribute('aria-label')).toBe('Not found across the XTM Platform');
  });

  it('shows not connected with a Connect entry to a configurer only', () => {
    const entries = [{
      def: def('opencrq'),
      answer: answer('opencrq', 'not_connected'),
    }];
    expect(visibleSections(entries, false)).toHaveLength(0);
    renderView(<XtmCounterpartsEmblemView sections={visibleSections(entries, true)} />);
    fireEvent.click(screen.getByTestId('xtm-emblem'));
    expect(screen.getByTestId('opencrq-connect').textContent).toBe('Connect OpenCRQ');
  });

  it('draws nothing when there is nothing to show (loading, or not connected for a reader)', () => {
    const { container } = renderView(
      <XtmCounterpartsEmblemView
        sections={visibleSections([
          {
            def: def('opencti'),
            answer: answer('opencti', 'loading'),
          },
          {
            def: def('opencrq'),
            answer: answer('opencrq', 'not_connected'),
          },
        ], false)}
      />,
    );
    expect(container.querySelector('[data-testid="xtm-emblem"]')).toBeNull();
  });

  it('never follows a non-http counterpart link', () => {
    expect(safeCounterpartUrl('javascript:alert(1)')).toBeUndefined();
    expect(safeCounterpartUrl('https://a.example/b')).toBe('https://a.example/b');
  });

  it('leaves XTM One out for a kind its inventory does not hold', () => {
    useCounterpartMock.mockImplementation((_kind: string, _key: string, product: string) => answer(product, 'no_match'));
    renderView(<XtmCounterpartsEmblem kind="vulnerabilities" objectKey="CVE-2024-0001" />);
    fireEvent.click(screen.getByTestId('xtm-emblem'));
    expect(screen.queryByTestId('xtmone-section')).toBeNull();
    expect(screen.getByTestId('opencti-section')).toBeTruthy();
  });
});
