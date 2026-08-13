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

/** The library's counter badge is 20px square minimum (`h-5 min-w-5`). */
const LIBRARY_COUNTER_SIZE = ['h-5', 'min-w-5'];

describe('BulkOperationsIndicator running counter', () => {
  afterEach(() => {
    cleanup();
    running = 0;
  });

  it('counts running operations with the library Badge, not a MUI one', () => {
    // Compensation #22 is retired: the library shipped a Badge (#114), so the
    // MUI one is no longer a filed gap, it is debt with a replacement.
    running = 3;
    renderIndicator();
    const counter = screen.getByText('3');
    const classes = String(counter.getAttribute('class') ?? '');
    for (const cls of LIBRARY_COUNTER_SIZE) expect(classes).toContain(cls);
    // Sandy's arbitration: it displays a count, so it takes the library's
    // default 20px counter - the 16px -> 20px growth is assumed.
    expect(document.querySelectorAll('[class*="MuiBadge-"]')).toHaveLength(0);
  });

  it('renders no counter when nothing is running', () => {
    renderIndicator();
    expect(screen.queryByText('0')).toBeNull();
    expect(document.querySelectorAll('[class*="MuiBadge-"]')).toHaveLength(0);
  });
});
