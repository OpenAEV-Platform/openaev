import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ConnectOpenCtiCta, OpenCtiMatchPreview, OpenCtiPivotView, OpenCtiRowMark } from '../../../components/xtm/XtmPivot';

const can = vi.fn();
const useCounterpartMock = vi.fn();
const graphEnabled = vi.fn(() => true);
vi.mock('../../../utils/hooks/useExplorationGraph', () => ({ default: () => graphEnabled() }));
vi.mock('../../../components/xtm/useCounterpart', () => ({
  default: (...args: unknown[]) => useCounterpartMock(...args),
  OPENCTI_PRODUCT: 'opencti',
}));
vi.mock('../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (s: string) => s,
    n: (v: number) => String(v),
    fsd: (d: string) => d.slice(0, 10),
  }),
}));
vi.mock('../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can }) }));
vi.mock('../../../actions/xtm/counterpart-actions', () => ({
  fetchXtmConnections: vi.fn(),
  lookupCounterparts: vi.fn(),
}));

const renderPivot = (element: ReactElement) => render(
  <ThemeProvider theme={createTheme()}><TooltipProvider>{element}</TooltipProvider></ThemeProvider>,
);

describe('OpenCtiPivotView', () => {
  beforeEach(() => {
    window.localStorage.clear();
    can.mockReturnValue(true);
  });
  afterEach(cleanup);

  it('given a match should link to the counterpart', () => {
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'match',
        product: 'opencti',
        counterpart: {
          counterpart_url: 'https://octi.example/dashboard/id/abc',
          counterpart_name: 'srv-01',
        },
      }}
      />,
    );
    const link = screen.getByTestId('xtm-pivot-open');
    expect(link.getAttribute('href')).toBe('https://octi.example/dashboard/id/abc');
    expect(screen.getByText('Open in OpenCTI')).toBeTruthy();
  });

  it('given a match with reports should carry the count on the button', () => {
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'match',
        product: 'opencti',
        counterpart: {
          counterpart_url: 'https://octi.example/dashboard/id/abc',
          counterpart_name: 'T1059',
          counterpart_facts: [{
            fact_key: 'reports',
            fact_value: '12',
          }],
        },
      }}
      />,
    );
    expect(screen.getByTestId('xtm-pivot-figure').textContent).toBe('· Reports 12');
  });

  it('given a match with zero reports should keep the label alone', () => {
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'match',
        product: 'opencti',
        counterpart: {
          counterpart_url: 'https://octi.example/dashboard/id/abc',
          counterpart_facts: [{
            fact_key: 'reports',
            fact_value: '0',
          }],
        },
      }}
      />,
    );
    expect(screen.queryByTestId('xtm-pivot-figure')).toBeNull();
  });

  it('given the panel size should render the match at a different size than the hero', () => {
    const answer = {
      state: 'match' as const,
      product: 'opencti',
      counterpart: {
        counterpart_url: 'https://octi.example/dashboard/id/abc',
        counterpart_name: 'srv-01',
      },
    };
    renderPivot(<OpenCtiPivotView answer={answer} />);
    const hero = screen.getByTestId('xtm-pivot-open').className;
    cleanup();
    renderPivot(<OpenCtiPivotView answer={answer} size="sm" />);
    const panel = screen.getByTestId('xtm-pivot-open').className;
    expect(panel).not.toBe(hero);
  });

  it('given connected without a match should say so', () => {
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'no_match',
        product: 'opencti',
      }}
      />,
    );
    expect(screen.getByText('Not in OpenCTI')).toBeTruthy();
    // A status, never a dead button.
    expect(screen.getByTestId('xtm-pivot-none').querySelector('button')).toBeNull();
  });

  it('given not connected and an administrator should offer to connect', () => {
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'not_connected',
        product: 'opencti',
      }}
      />,
    );
    expect(screen.getByTestId('xtm-connect-cta')).toBeTruthy();
    expect(screen.getByText('Connect OpenCTI')).toBeTruthy();
  });

  it('given not connected and no right to configure should show the read-only hint', () => {
    can.mockReturnValue(false);
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'not_connected',
        product: 'opencti',
      }}
      />,
    );
    expect(screen.queryByTestId('xtm-connect-cta')).toBeNull();
    expect(screen.getByTestId('xtm-connect-hint').querySelector('button')).toBeNull();
  });

  it('given an unavailable product should degrade to a muted mark, never an error', () => {
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'unavailable',
        product: 'opencti',
      }}
      />,
    );
    expect(screen.getByTestId('xtm-pivot-unavailable').querySelector('button')).toBeNull();
  });

  it('given a dismissed call to action should stay hidden', () => {
    renderPivot(<ConnectOpenCtiCta />);
    fireEvent.click(screen.getByLabelText('Hide this suggestion'));
    cleanup();
    renderPivot(<ConnectOpenCtiCta />);
    expect(screen.queryByTestId('xtm-connect-cta')).toBeNull();
  });
});

describe('OpenCtiMatchPreview', () => {
  afterEach(cleanup);

  it('given a match should show the type, the name, the OpenCTI facts in order and the action', () => {
    renderPivot(
      <OpenCtiMatchPreview answer={{
        state: 'match',
        product: 'opencti',
        counterpart: {
          counterpart_entity_type: 'Attack-Pattern',
          counterpart_name: 'Command and Scripting Interpreter',
          counterpart_facts: [
            {
              fact_key: 'markings',
              fact_value: 'TLP:GREEN',
            },
            {
              fact_key: 'reports',
              fact_value: '12',
            },
            {
              fact_key: 'last_modified',
              fact_value: '2026-11-20T10:00:00.000Z',
            },
          ],
        },
      }}
      />,
    );
    expect(screen.getByText('Attack-Pattern')).toBeTruthy();
    expect(screen.getByText('Command and Scripting Interpreter')).toBeTruthy();
    expect(screen.getByTestId('xtm-preview-facts').textContent).toBe('Reports12Last modified2026-11-20MarkingsTLP:GREEN');
    expect(screen.getByText('See the reports, relationships and context of this object in OpenCTI.')).toBeTruthy();
  });
});

describe('OpenCtiRowMark', () => {
  afterEach(cleanup);

  const match = {
    state: 'match',
    product: 'opencti',
    counterpart: {
      counterpart_url: 'https://octi.example/dashboard/id/alert',
      counterpart_name: 'Incident 1',
    },
  };

  it('given the exploration graph feature off should render nothing and never look the object up', () => {
    graphEnabled.mockReturnValueOnce(false);
    useCounterpartMock.mockClear();
    const { container } = renderPivot(<OpenCtiRowMark kind="alerts" objectKey="https://edr/1" reserveSlot />);
    expect(container.textContent).toBe('');
    expect(screen.queryByTestId('xtm-row-mark-slot')).toBeNull();
    expect(useCounterpartMock).not.toHaveBeenCalled();
  });

  it('given a match with a reserved slot should render the mark inside a fixed-width slot', () => {
    useCounterpartMock.mockReturnValue(match);
    renderPivot(<OpenCtiRowMark kind="alerts" objectKey="https://edr/1" label="Investigate in OpenCTI" reserveSlot />);
    const slot = screen.getByTestId('xtm-row-mark-slot');
    expect(slot.querySelector('[data-testid="xtm-row-mark"]')).toBeTruthy();
  });

  it('given a match should be a keyboard-focusable link named by its action', () => {
    useCounterpartMock.mockReturnValue(match);
    renderPivot(<OpenCtiRowMark kind="alerts" objectKey="https://edr/1" label="Investigate in OpenCTI" />);
    const mark = screen.getByTestId('xtm-row-mark');
    expect(mark.tagName).toBe('A');
    expect(mark.getAttribute('aria-label')).toBe('Investigate in OpenCTI');
    mark.focus();
    expect(document.activeElement).toBe(mark);
  });

  it('given no match with a reserved slot should keep an empty slot so the row controls stay aligned', () => {
    useCounterpartMock.mockReturnValue({
      state: 'no_match',
      product: 'opencti',
    });
    renderPivot(<OpenCtiRowMark kind="alerts" objectKey="https://edr/2" reserveSlot />);
    const slot = screen.getByTestId('xtm-row-mark-slot');
    expect(slot.children.length).toBe(0);
    expect(screen.queryByTestId('xtm-row-mark')).toBeNull();
  });

  it('given not connected with a reserved slot should render nothing', () => {
    useCounterpartMock.mockReturnValue({
      state: 'not_connected',
      product: 'opencti',
    });
    renderPivot(<OpenCtiRowMark kind="alerts" objectKey="https://edr/3" reserveSlot />);
    expect(screen.queryByTestId('xtm-row-mark-slot')).toBeNull();
  });

  it('given no match without a reserved slot should render nothing', () => {
    useCounterpartMock.mockReturnValue({
      state: 'no_match',
      product: 'opencti',
    });
    renderPivot(<OpenCtiRowMark kind="vulnerabilities" objectKey="CVE-2023-99999" />);
    expect(screen.queryByTestId('xtm-row-mark-slot')).toBeNull();
    expect(screen.queryByTestId('xtm-row-mark')).toBeNull();
  });
});
