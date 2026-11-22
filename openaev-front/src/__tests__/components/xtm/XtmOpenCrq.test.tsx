import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { orderedFacts } from '../../../components/xtm/counterpartFacts';
import { OpenCrqMatchPreview, OpenCrqPivotView, OpenCrqRowMark } from '../../../components/xtm/XtmOpenCrq';

const can = vi.fn();
const useCounterpartMock = vi.fn();
const graphEnabled = vi.fn(() => true);
vi.mock('../../../utils/hooks/useExplorationGraph', () => ({ default: () => graphEnabled() }));
vi.mock('../../../components/xtm/useCounterpart', () => ({ default: (...args: unknown[]) => useCounterpartMock(...args) }));
vi.mock('../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (s: string) => s,
    n: (v: number) => `${v / 1000}K`,
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

const MATCH = {
  state: 'match' as const,
  product: 'opencrq',
  counterpart: {
    counterpart_url: 'https://ocrq.example/cve/CVE-2024-0001',
    counterpart_name: 'CVE-2024-0001',
    counterpart_facts: [
      {
        fact_key: 'annual_loss',
        fact_value: '125000',
      },
      {
        fact_key: 'risk_scenarios',
        fact_value: '3',
      },
      {
        fact_key: 'unknown',
        fact_value: 'x',
      },
    ],
  },
};

describe('OpenCrqPivotView (contract v3: icon-only emblem)', () => {
  beforeEach(() => {
    can.mockReturnValue(true);
  });
  afterEach(cleanup);

  it('given a match should be an icon-only link to the OpenCRQ object, named, with no text', () => {
    renderView(<OpenCrqPivotView answer={MATCH} />);
    const link = screen.getByTestId('xtm-crq-pivot-found');
    expect(link.getAttribute('href')).toBe('https://ocrq.example/cve/CVE-2024-0001');
    expect(link.getAttribute('aria-label')).toBe('Open in OpenCRQ');
    expect(link.textContent).toBe('');
  });

  it('given a match should render at the same FDS IconButton md size as the OpenCTI emblem', () => {
    renderView(<OpenCrqPivotView answer={MATCH} />);
    const className = screen.getByTestId('xtm-crq-pivot-found').className;
    expect(className).toContain('h-9');
    expect(className).toContain('w-9');
  });

  it('given connected without a match should show the greyed emblem named "Not found in OpenCRQ"', () => {
    renderView(
      <OpenCrqPivotView answer={{
        state: 'no_match',
        product: 'opencrq',
      }}
      />,
    );
    const button = screen.getByTestId('xtm-crq-pivot-not-found');
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.getAttribute('aria-label')).toBe('Not found in OpenCRQ');
  });

  it('given not connected and an administrator should link the greyed emblem to the connection guide', () => {
    renderView(
      <OpenCrqPivotView answer={{
        state: 'not_connected',
        product: 'opencrq',
      }}
      />,
    );
    const link = screen.getByTestId('xtm-crq-pivot-not-connected');
    expect(link.tagName).toBe('A');
    expect(link.getAttribute('aria-label')).toBe('Connect OpenCRQ');
  });

  it('given not connected and no right to configure should show the greyed emblem with no action', () => {
    can.mockReturnValue(false);
    renderView(
      <OpenCrqPivotView answer={{
        state: 'not_connected',
        product: 'opencrq',
      }}
      />,
    );
    const button = screen.getByTestId('xtm-crq-pivot-not-connected');
    expect(button.tagName).toBe('BUTTON');
    expect(button.getAttribute('aria-label')).toBe('OpenCRQ is not connected');
  });

  it('given an unavailable OpenCRQ should show the greyed emblem with no action', () => {
    renderView(
      <OpenCrqPivotView answer={{
        state: 'unavailable',
        product: 'opencrq',
      }}
      />,
    );
    expect(screen.getByTestId('xtm-crq-pivot-unavailable').getAttribute('aria-disabled')).toBe('true');
  });
});

describe('OpenCrqMatchPreview', () => {
  afterEach(cleanup);

  it('given a match should show the object, its facts and what OpenCRQ opens', () => {
    renderView(<OpenCrqMatchPreview answer={MATCH} />);
    expect(screen.getByText('CVE-2024-0001')).toBeTruthy();
    expect(screen.getByTestId('xtm-preview-facts').textContent).toBe('Risk scenarios3Annual loss expectancy125K');
    expect(screen.getByText('See the financial impact of this object and the risk scenarios it drives in OpenCRQ.')).toBeTruthy();
  });

  it('given a match without facts should show the object and the action, no fact list', () => {
    renderView(
      <OpenCrqMatchPreview answer={{
        ...MATCH,
        counterpart: {
          ...MATCH.counterpart,
          counterpart_facts: [],
        },
      }}
      />,
    );
    expect(screen.queryByTestId('xtm-preview-facts')).toBeNull();
    expect(screen.getByTestId('xtm-preview')).toBeTruthy();
  });
});

describe('OpenCrqRowMark', () => {
  afterEach(cleanup);

  it('given the exploration graph feature off should render nothing and never look the object up', () => {
    graphEnabled.mockReturnValueOnce(false);
    useCounterpartMock.mockClear();
    const { container } = renderView(<OpenCrqRowMark kind="vulnerabilities" objectKey="CVE-2024-0001" reserveSlot />);
    expect(container.textContent).toBe('');
    expect(useCounterpartMock).not.toHaveBeenCalled();
  });

  it('given a match should link the OpenCRQ mark to the object', () => {
    useCounterpartMock.mockReturnValue(MATCH);
    renderView(<OpenCrqRowMark kind="vulnerabilities" objectKey="CVE-2024-0001" />);
    expect(screen.getByTestId('xtm-crq-row-mark').getAttribute('href')).toBe('https://ocrq.example/cve/CVE-2024-0001');
    expect(useCounterpartMock).toHaveBeenCalledWith('vulnerabilities', 'CVE-2024-0001', 'opencrq');
  });

  it('given connected without a match should render nothing, not even a reserved slot', () => {
    useCounterpartMock.mockReturnValue({
      state: 'no_match',
      product: 'opencrq',
    });
    const { container } = renderView(<OpenCrqRowMark kind="assets" objectKey="host-1" reserveSlot />);
    expect(container.textContent).toBe('');
    expect(screen.queryByTestId('xtm-crq-row-mark-slot')).toBeNull();
    expect(screen.queryByTestId('xtm-crq-row-mark')).toBeNull();
  });

  it('given a loading answer with a reserved slot should keep an empty slot so the row does not shift', () => {
    useCounterpartMock.mockReturnValue({
      state: 'loading',
      product: 'opencrq',
    });
    renderView(<OpenCrqRowMark kind="assets" objectKey="host-2" reserveSlot />);
    expect(screen.getByTestId('xtm-crq-row-mark-slot').children.length).toBe(0);
  });

  it('given not connected should render nothing, even with a reserved slot', () => {
    useCounterpartMock.mockReturnValue({
      state: 'not_connected',
      product: 'opencrq',
    });
    renderView(<OpenCrqRowMark kind="assets" objectKey="host-1" reserveSlot />);
    expect(screen.queryByTestId('xtm-crq-row-mark-slot')).toBeNull();
  });
});

describe('orderedFacts', () => {
  it('should keep known keys with a value, in display order', () => {
    expect(orderedFacts([
      {
        fact_key: 'fix_value',
        fact_value: '9',
      },
      {
        fact_key: 'annual_loss',
        fact_value: '',
      },
      {
        fact_key: 'risk_scenarios',
        fact_value: '2',
      },
    ]).map(f => f.fact_key)).toEqual(['risk_scenarios', 'fix_value']);
  });
});
