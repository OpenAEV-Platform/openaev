import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { withSourceLink } from '../../../components/xtm/sourcedCounterpart';
import { CONNECT_OPENCTI_DOC_URL, OpenCtiMatchPreview, OpenCtiPivotView, OpenCtiRowMark } from '../../../components/xtm/XtmPivot';

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

describe('OpenCtiPivotView (contract v3: icon-only emblem)', () => {
  beforeEach(() => {
    can.mockReturnValue(true);
  });
  afterEach(cleanup);

  const match = {
    state: 'match' as const,
    product: 'opencti',
    counterpart: {
      counterpart_url: 'https://octi.example/dashboard/id/abc',
      counterpart_name: 'srv-01',
      counterpart_facts: [{
        fact_key: 'reports',
        fact_value: '12',
      }],
    },
  };

  it('given a match should be an icon-only link to the counterpart, named, with no text', () => {
    renderPivot(<OpenCtiPivotView answer={match} />);
    const link = screen.getByTestId('xtm-pivot-found');
    expect(link.tagName).toBe('A');
    expect(link.getAttribute('href')).toBe('https://octi.example/dashboard/id/abc');
    expect(link.getAttribute('aria-label')).toBe('Open in OpenCTI');
    expect(link.textContent).toBe('');
    expect(link.getAttribute('aria-disabled')).toBeNull();
  });

  it('given a match should render at the FDS IconButton md size of the header actions (36 px)', () => {
    renderPivot(<OpenCtiPivotView answer={match} />);
    const className = screen.getByTestId('xtm-pivot-found').className;
    expect(className).toContain('h-9');
    expect(className).toContain('w-9');
  });

  it('given connected and not found should show the greyed emblem, focusable, named "Not found in OpenCTI"', () => {
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'no_match',
        product: 'opencti',
      }}
      />,
    );
    const button = screen.getByTestId('xtm-pivot-not-found');
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.hasAttribute('disabled')).toBe(false);
    expect(button.getAttribute('aria-label')).toBe('Not found in OpenCTI');
    expect(button.querySelector('[data-testid="xtm-emblem-mask"]')).toBeTruthy();
    button.focus();
    expect(document.activeElement).toBe(button);
  });

  it('given not connected and an administrator should show the greyed emblem linking to the connection guide', () => {
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'not_connected',
        product: 'opencti',
      }}
      />,
    );
    const link = screen.getByTestId('xtm-pivot-not-connected');
    expect(link.tagName).toBe('A');
    expect(link.getAttribute('href')).toBe(CONNECT_OPENCTI_DOC_URL);
    expect(link.getAttribute('aria-label')).toBe('Connect OpenCTI');
    expect(link.querySelector('[data-testid="xtm-emblem-mask"]')).toBeTruthy();
  });

  it('given not connected and no right to configure should show the greyed emblem with no action', () => {
    can.mockReturnValue(false);
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'not_connected',
        product: 'opencti',
      }}
      />,
    );
    const button = screen.getByTestId('xtm-pivot-not-connected');
    expect(button.tagName).toBe('BUTTON');
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.getAttribute('aria-label')).toBe('OpenCTI is not connected');
  });

  it('given an unavailable OpenCTI should show the greyed emblem with no action', () => {
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'unavailable',
        product: 'opencti',
      }}
      />,
    );
    expect(screen.getByTestId('xtm-pivot-unavailable').getAttribute('aria-disabled')).toBe('true');
  });

  it('given loading should render nothing yet', () => {
    const { container } = renderPivot(
      <OpenCtiPivotView answer={{
        state: 'loading',
        product: 'opencti',
      }}
      />,
    );
    expect(container.textContent).toBe('');
    expect(container.querySelector('button, a')).toBeNull();
  });
});

describe('withSourceLink (contract v3, rule 5)', () => {
  const connectionUrl = 'https://octi.example/';

  it('given an object sourced from the connected product should be found from its source link', () => {
    const answer = withSourceLink({
      state: 'no_match',
      product: 'opencti',
      connectionUrl,
    }, 'https://octi.example/dashboard/id/xyz');
    expect(answer.state).toBe('match');
    expect(answer.counterpart?.counterpart_url).toBe('https://octi.example/dashboard/id/xyz');
  });

  it('given a source link into another system should keep the lookup answer', () => {
    const answer = withSourceLink({
      state: 'no_match',
      product: 'opencti',
      connectionUrl,
    }, 'https://edr.example/devices/1');
    expect(answer.state).toBe('no_match');
  });

  it('given a lookup match should keep the match and its facts', () => {
    const lookup = {
      state: 'match' as const,
      product: 'opencti',
      connectionUrl,
      counterpart: { counterpart_url: 'https://octi.example/dashboard/id/abc' },
    };
    expect(withSourceLink(lookup, 'https://octi.example/dashboard/id/xyz')).toBe(lookup);
  });

  it('given not connected or a source that is not a URL should keep the answer', () => {
    expect(withSourceLink({
      state: 'not_connected',
      product: 'opencti',
    }, 'https://octi.example/x').state).toBe('not_connected');
    expect(withSourceLink({
      state: 'no_match',
      product: 'opencti',
      connectionUrl,
    }, 'device-42').state).toBe('no_match');
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

  it('given connected without a match and a reserved slot should render nothing, no empty slot', () => {
    useCounterpartMock.mockReturnValue({
      state: 'no_match',
      product: 'opencti',
    });
    const { container } = renderPivot(<OpenCtiRowMark kind="alerts" objectKey="https://edr/2" reserveSlot />);
    expect(container.textContent).toBe('');
    expect(screen.queryByTestId('xtm-row-mark-slot')).toBeNull();
    expect(screen.queryByTestId('xtm-row-mark')).toBeNull();
  });

  it('given a loading answer with a reserved slot should keep an empty slot so the row does not shift', () => {
    useCounterpartMock.mockReturnValue({
      state: 'loading',
      product: 'opencti',
    });
    renderPivot(<OpenCtiRowMark kind="alerts" objectKey="https://edr/4" reserveSlot />);
    expect(screen.getByTestId('xtm-row-mark-slot').children.length).toBe(0);
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
