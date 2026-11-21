import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { orderedFacts } from '../../../components/xtm/counterpartFacts';
import { ConnectOpenCrqCta, OpenCrqCardView, OpenCrqMatchPreview, OpenCrqPivotView, OpenCrqRowMark } from '../../../components/xtm/XtmOpenCrq';

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

describe('OpenCrqPivotView', () => {
  beforeEach(() => {
    window.localStorage.clear();
    can.mockReturnValue(true);
  });
  afterEach(cleanup);

  it('given a match should link to the OpenCRQ object', () => {
    renderView(<OpenCrqPivotView answer={MATCH} />);
    expect(screen.getByTestId('xtm-crq-pivot-open').getAttribute('href')).toBe('https://ocrq.example/cve/CVE-2024-0001');
    expect(screen.getByText('Open in OpenCRQ')).toBeTruthy();
  });

  it('given a match with an annual loss should carry the key figure on the button', () => {
    renderView(<OpenCrqPivotView answer={MATCH} />);
    expect(screen.getByTestId('xtm-crq-pivot-figure').textContent).toBe('· Annual loss 125K');
  });

  it('given a match without facts should keep the label alone', () => {
    renderView(
      <OpenCrqPivotView answer={{
        ...MATCH,
        counterpart: {
          ...MATCH.counterpart,
          counterpart_facts: [],
        },
      }}
      />,
    );
    expect(screen.getByText('Open in OpenCRQ')).toBeTruthy();
    expect(screen.queryByTestId('xtm-crq-pivot-figure')).toBeNull();
  });

  it('given the default size should render the hero at the FDS md size, never sm', () => {
    renderView(<OpenCrqPivotView answer={MATCH} />);
    const hero = screen.getByTestId('xtm-crq-pivot-open').className;
    cleanup();
    renderView(<OpenCrqPivotView answer={MATCH} size="md" />);
    expect(screen.getByTestId('xtm-crq-pivot-open').className).toBe(hero);
  });

  it('given connected without a match should say so', () => {
    renderView(
      <OpenCrqPivotView answer={{
        state: 'no_match',
        product: 'opencrq',
      }}
      />,
    );
    expect(screen.getByText('Not in OpenCRQ')).toBeTruthy();
  });

  it('given not connected and an administrator should offer to connect OpenCRQ', () => {
    renderView(
      <OpenCrqPivotView answer={{
        state: 'not_connected',
        product: 'opencrq',
      }}
      />,
    );
    expect(screen.getByText('Connect OpenCRQ')).toBeTruthy();
  });

  it('given not connected and no right to configure should show the read-only hint', () => {
    can.mockReturnValue(false);
    renderView(
      <OpenCrqPivotView answer={{
        state: 'not_connected',
        product: 'opencrq',
      }}
      />,
    );
    expect(screen.queryByTestId('xtm-crq-connect-cta')).toBeNull();
    expect(screen.getByTestId('xtm-crq-connect-hint').querySelector('button')).toBeNull();
  });

  it('given an unavailable OpenCRQ should degrade to a muted mark', () => {
    renderView(
      <OpenCrqPivotView answer={{
        state: 'unavailable',
        product: 'opencrq',
      }}
      />,
    );
    expect(screen.getByTestId('xtm-crq-pivot-unavailable').querySelector('button')).toBeNull();
  });

  it('given a dismissed call to action should stay hidden', () => {
    renderView(<ConnectOpenCrqCta />);
    fireEvent.click(screen.getByLabelText('Hide this suggestion'));
    cleanup();
    renderView(<ConnectOpenCrqCta />);
    expect(screen.queryByTestId('xtm-crq-connect-cta')).toBeNull();
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

describe('OpenCrqCardView', () => {
  afterEach(cleanup);

  it('given a match should show the known facts in order with amounts formatted', () => {
    renderView(<OpenCrqCardView answer={MATCH} />);
    const facts = screen.getByTestId('xtm-crq-facts').textContent;
    expect(facts).toBe('Risk scenarios3Annual loss expectancy125K');
  });

  it('given no match should explain it', () => {
    renderView(
      <OpenCrqCardView answer={{
        state: 'no_match',
        product: 'opencrq',
      }}
      />,
    );
    expect(screen.getByText('This object is not known in the connected OpenCRQ.')).toBeTruthy();
  });

  it('given loading should render nothing', () => {
    const { container } = renderView(
      <OpenCrqCardView answer={{
        state: 'loading',
        product: 'opencrq',
      }}
      />,
    );
    expect(container.textContent).toBe('');
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

  it('given connected without a match should keep an empty slot only when asked', () => {
    useCounterpartMock.mockReturnValue({
      state: 'no_match',
      product: 'opencrq',
    });
    renderView(<OpenCrqRowMark kind="assets" objectKey="host-1" reserveSlot />);
    expect(screen.getByTestId('xtm-crq-row-mark-slot')).toBeTruthy();
    expect(screen.queryByTestId('xtm-crq-row-mark')).toBeNull();
    cleanup();
    const { container } = renderView(<OpenCrqRowMark kind="assets" objectKey="host-1" />);
    expect(container.textContent).toBe('');
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
