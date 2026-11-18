import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ConnectOpenCtiCta, OpenCtiPivotView } from '../../../components/xtm/XtmPivot';

const can = vi.fn();
vi.mock('../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));
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
    renderPivot(<OpenCtiPivotView answer={answer} size="md" />);
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
    expect(screen.getByTestId('xtm-connect-hint')).toBeTruthy();
  });

  it('given an unavailable product should degrade to a muted mark, never an error', () => {
    renderPivot(
      <OpenCtiPivotView answer={{
        state: 'unavailable',
        product: 'opencti',
      }}
      />,
    );
    expect(screen.getByTestId('xtm-pivot-unavailable')).toBeTruthy();
  });

  it('given a dismissed call to action should stay hidden', () => {
    renderPivot(<ConnectOpenCtiCta />);
    fireEvent.click(screen.getByLabelText('Hide this suggestion'));
    cleanup();
    renderPivot(<ConnectOpenCtiCta />);
    expect(screen.queryByTestId('xtm-connect-cta')).toBeNull();
  });
});
