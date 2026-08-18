// `@testing-library/jest-dom` provides `toHaveAccessibleName` /
// `toHaveAccessibleDescription`, which compute what a screen reader is handed —
// following aria-describedby and honouring aria-hidden. Imported here rather than
// in a global setup file: it costs ~2.5s per test file and only two files need it.
import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { expectNoMuiControls } from '../../../utils/designSystemAssertions';

// Only the rendering matters here, not the SSE plumbing behind it. `running` is
// a count of RUNNING rows; `statuses` drives an explicit mix when a test needs one.
let running = 0;
let statuses: string[] | null = null;
vi.mock('../../../../utils/bulkOperations', () => ({
  seedBulkOperations: () => {},
  useBulkOperations: () => (statuses ?? Array.from({ length: running }, () => 'RUNNING'))
    .map((status, index) => ({
      bulk_operation_id: `op-${index}`,
      bulk_operation_status: status,
      bulk_operation_action: 'delete',
      bulk_operation_entity: 'scenarios',
      bulk_operation_processed: 1,
      bulk_operation_total: 10,
    })),
}));

const { default: BulkOperationsIndicator } = await import('../../../../admin/components/nav/BulkOperationsIndicator');

const renderIndicator = () => render(
  <ThemeProvider theme={createTheme()}>
    <IntlProvider locale="en" messages={{}}>
      <TooltipProvider>
        <BulkOperationsIndicator />
      </TooltipProvider>
    </IntlProvider>
  </ThemeProvider>,
);

const trigger = () => screen.getByRole('button', { name: 'bulk-operations-menu' });

/** The library's counter badge is 20px square minimum (`h-5 min-w-5`). */
const LIBRARY_COUNTER_SIZE = ['h-5', 'min-w-5'];

describe('BulkOperationsIndicator running counter', () => {
  afterEach(() => {
    cleanup();
    running = 0;
    statuses = null;
  });

  it('counts running operations with the library Badge, not a MUI one', () => {
    running = 3;
    renderIndicator();
    const counter = screen.getByText('3');
    const classes = String(counter.getAttribute('class') ?? '');
    // Arbitrated: it displays a count, so it takes the library's
    // default 20px counter - the 16px -> 20px growth is assumed.
    for (const cls of LIBRARY_COUNTER_SIZE) expect(classes).toContain(cls);
    expect(document.querySelectorAll('[class*="MuiBadge-"]')).toHaveLength(0);
  });

  it('announces the count to assistive technology, not merely into the DOM', () => {
    // Same defect as the bell: the badge used to live in the icon slot, which
    // the library's IconButton renders `aria-hidden`. Asserted on the computed
    // description so a relapse cannot pass.
    running = 5;
    renderIndicator();
    expect(trigger()).toHaveAccessibleDescription('5');
    expect(trigger()).toHaveAccessibleName('bulk-operations-menu');
  });

  it('shows progress with the library components, not MUI ones', async () => {
    // The library shipped Spinner + ProgressBar (#115), so the dated exemption
    // that tolerated MUI progress in the guard is gone and this must hold.
    running = 2;
    renderIndicator();
    // The ring beside the glyph, and the per-operation bars inside the panel.
    fireEvent.click(trigger());
    await waitFor(() => expect(screen.getAllByRole('progressbar').length).toBeGreaterThan(0));
    expectNoMuiControls(document.body, 'the bulk-operations button and its panel');
    // The bars stay determinate: the value is what a screen reader reads.
    for (const bar of screen.getAllByRole('progressbar')) {
      expect(bar.getAttribute('aria-valuenow')).not.toBeNull();
    }
  });

  it('colours each bar by its operation status', async () => {
    // The library gained a `tone` axis (#118), so the per-status colour the
    // product had before the migration comes back — from tokens, not from `sx`.
    statuses = ['RUNNING', 'COMPLETED', 'FAILED'];
    renderIndicator();
    fireEvent.click(trigger());
    await waitFor(() => expect(screen.getAllByRole('progressbar')).toHaveLength(3));
    const fills = screen.getAllByRole('progressbar')
      .map(bar => String(bar.firstElementChild?.getAttribute('class') ?? ''));
    expect(fills[0]).toContain('bg-filigran-brand-primary');
    expect(fills[1]).toContain('bg-feedback-success-primary');
    expect(fills[2]).toContain('bg-feedback-error-primary');
  });

  it('says nothing when nothing is running', () => {
    renderIndicator();
    expect(screen.queryByText('0')).toBeNull();
    expect(trigger()).toHaveAccessibleDescription('');
    expect(document.querySelectorAll('[class*="MuiBadge-"]')).toHaveLength(0);
  });
});
