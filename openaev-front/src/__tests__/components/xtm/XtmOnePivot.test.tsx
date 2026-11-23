import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen, within } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { EMBLEM_FOUND_XTM_ONE, EMBLEM_MUTED } from '../../../components/xtm/XtmEmblem';
import XtmOnePivot, { XTM_ONE_PRODUCT, XtmOneMatchPreview, XtmOnePivotView } from '../../../components/xtm/XtmOnePivot';

const can = vi.fn();
const useCounterpartMock = vi.fn();
vi.mock('../../../components/xtm/useCounterpart', () => ({ default: (...args: unknown[]) => useCounterpartMock(...args) }));
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

const MATCH = {
  state: 'match' as const,
  product: 'xtmone',
  counterpart: {
    counterpart_url: 'https://xtm-one.example/ctem/inventory/assets/7f1c',
    counterpart_name: 'web-02',
    counterpart_entity_type: 'asset',
    counterpart_facts: [
      {
        fact_key: 'criticality',
        fact_value: 'high',
      },
      {
        fact_key: 'category',
        fact_value: 'server',
      },
    ],
  },
};

describe('XtmOnePivotView (contract v3: icon-only emblem, canonical XTM One purple)', () => {
  beforeEach(() => {
    can.mockReturnValue(true);
  });
  afterEach(cleanup);

  it('given a match should be an icon-only link to the inventory asset, named, with no text', () => {
    renderView(<XtmOnePivotView answer={MATCH} />);
    const link = screen.getByTestId('xtm-one-pivot-found');
    expect(link.getAttribute('href')).toBe('https://xtm-one.example/ctem/inventory/assets/7f1c');
    expect(link.getAttribute('aria-label')).toBe('Open in XTM One');
    expect(link.textContent).toBe('');
  });

  it('given a match should paint the glyph in the canonical XTM One purple, at the md emblem size', () => {
    renderView(<XtmOnePivotView answer={MATCH} />);
    const link = screen.getByTestId('xtm-one-pivot-found');
    expect(link.className).toContain('h-9');
    expect(EMBLEM_FOUND_XTM_ONE).toBe('var(--color-filigran-ia-primary)');
    expect(within(link).getByTestId('xtm-emblem-mask')).toBeTruthy();
  });

  it('given connected without a match should show the greyed emblem named "Not found in XTM One"', () => {
    renderView(
      <XtmOnePivotView answer={{
        state: 'no_match',
        product: 'xtmone',
      }}
      />,
    );
    const button = screen.getByTestId('xtm-one-pivot-not-found');
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.getAttribute('aria-label')).toBe('Not found in XTM One');
    expect(EMBLEM_MUTED).toBe('var(--icon-disabled)');
  });

  it('given not connected and an administrator should link the greyed emblem to the connection guide', () => {
    renderView(
      <XtmOnePivotView answer={{
        state: 'not_connected',
        product: 'xtmone',
      }}
      />,
    );
    const link = screen.getByTestId('xtm-one-pivot-not-connected');
    expect(link.tagName).toBe('A');
    expect(link.getAttribute('aria-label')).toBe('Connect XTM One');
  });

  it('given not connected and no right to configure should show the greyed emblem with no action', () => {
    can.mockReturnValue(false);
    renderView(
      <XtmOnePivotView answer={{
        state: 'not_connected',
        product: 'xtmone',
      }}
      />,
    );
    const button = screen.getByTestId('xtm-one-pivot-not-connected');
    expect(button.tagName).toBe('BUTTON');
    expect(button.getAttribute('aria-label')).toBe('XTM One is not connected');
  });
});

describe('XtmOneMatchPreview', () => {
  afterEach(cleanup);

  it('given a match should show the asset name and the facts XTM One answered', () => {
    renderView(<XtmOneMatchPreview answer={MATCH} />);
    expect(screen.getByText('web-02')).toBeTruthy();
    expect(screen.getByText('high')).toBeTruthy();
    expect(screen.getByText('server')).toBeTruthy();
  });
});

describe('XtmOnePivot', () => {
  beforeEach(() => {
    can.mockReturnValue(true);
  });
  afterEach(cleanup);

  it('given an asset should look it up in the XTM One product, with no feature flag', () => {
    useCounterpartMock.mockReturnValue(MATCH);
    renderView(<XtmOnePivot kind="assets" objectKey="web-02" />);
    expect(useCounterpartMock).toHaveBeenCalledWith('assets', 'web-02', XTM_ONE_PRODUCT);
    expect(screen.getByTestId('xtm-one-pivot-found')).toBeTruthy();
  });
});
