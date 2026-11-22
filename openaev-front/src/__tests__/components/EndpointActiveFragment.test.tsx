import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import EndpointActiveFragment from '../../components/common/list/fragments/EndpointActiveFragment';

const interpolate = (message: string, values?: Record<string, string>) => Object.entries(values ?? {})
  .reduce((text, [key, value]) => text.replace(`{${key}}`, value), message);

vi.mock('../../components/i18n', () => ({
  useFormatter: () => ({
    t: interpolate,
    fldt: () => 'Nov 20, 2026',
  }),
}));

const renderFragment = (props: Parameters<typeof EndpointActiveFragment>[0]) => render(
  <TooltipProvider delayDuration={0}>
    <EndpointActiveFragment {...props} />
  </TooltipProvider>,
);

describe('EndpointActiveFragment', () => {
  afterEach(cleanup);

  it('renders Stale from the server status, whatever the agents say', () => {
    renderFragment({
      activity_map: [true],
      asset_status: 'STALE',
      asset_last_seen: '2026-11-20T10:00:00Z',
    });
    expect(screen.getByText('Stale')).toBeInTheDocument();
    expect(screen.queryByText('Active')).toBeNull();
  });

  it('names the last seen date and the threshold in the tooltip', async () => {
    renderFragment({
      asset_status: 'STALE',
      asset_last_seen: '2026-11-20T10:00:00Z',
      stale_after_days: 3,
    });
    const info = screen.getByRole('img', { name: 'Last seen Nov 20, 2026, threshold 3 days' });
    fireEvent.pointerMove(info);
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Last seen Nov 20, 2026, threshold 3 days');
  });

  it('opens the cause from the keyboard', async () => {
    renderFragment({
      asset_status: 'STALE',
      asset_last_seen: '2026-11-20T10:00:00Z',
      stale_after_days: 3,
    });
    const info = screen.getByRole('img', { name: 'Last seen Nov 20, 2026, threshold 3 days' });
    expect(info).toHaveAttribute('tabindex', '0');
    fireEvent.focus(info);
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Last seen Nov 20, 2026, threshold 3 days');
  });

  it('uses the singular for a one day threshold', async () => {
    renderFragment({
      asset_status: 'STALE',
      asset_last_seen: '2026-11-20T10:00:00Z',
      stale_after_days: 1,
    });
    fireEvent.pointerMove(screen.getByRole('img', { name: 'Last seen Nov 20, 2026, threshold 1 day' }));
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Last seen Nov 20, 2026, threshold 1 day');
  });

  it('keeps the agent-derived status when the asset is not stale', () => {
    renderFragment({
      activity_map: [],
      asset_status: 'AGENTLESS',
    });
    expect(screen.getByText('Agentless')).toBeInTheDocument();
  });
});
