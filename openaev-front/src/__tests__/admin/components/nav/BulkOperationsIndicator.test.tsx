// `@testing-library/jest-dom` provides `toHaveAccessibleName` /
// `toHaveAccessibleDescription`, which compute what a screen reader is handed —
// following aria-describedby and honouring aria-hidden. Imported here rather than
// in a global setup file: it costs ~2.5s per test file and only two files need it.
import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

// Only the counter matters here, not the SSE plumbing behind it.
let running = 0;
vi.mock('../../../../utils/bulkOperations', () => ({
  seedBulkOperations: () => {},
  useBulkOperations: () => Array.from({ length: running }, (_unused, index) => ({
    bulk_operation_id: `op-${index}`,
    bulk_operation_status: 'RUNNING',
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
  });

  it('counts running operations with the library Badge, not a MUI one', () => {
    running = 3;
    renderIndicator();
    const counter = screen.getByText('3');
    const classes = String(counter.getAttribute('class') ?? '');
    // Sandy's arbitration: it displays a count, so it takes the library's
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

  it('says nothing when nothing is running', () => {
    renderIndicator();
    expect(screen.queryByText('0')).toBeNull();
    expect(trigger()).toHaveAccessibleDescription('');
    expect(document.querySelectorAll('[class*="MuiBadge-"]')).toHaveLength(0);
  });
});
