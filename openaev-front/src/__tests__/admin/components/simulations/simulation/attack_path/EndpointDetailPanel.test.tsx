import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import EndpointDetailPanel from '../../../../../../admin/components/simulations/simulation/attack_path/EndpointDetailPanel';

// The run-status badge fetches on its own; stub it so these tests are about the findings list only.
vi.mock('../../../../../../admin/components/simulations/simulation/attack_path/ExecutionStatusBadge', () => ({ ExecutionRowStatusBadge: () => <span data-testid="exec-status" /> }));

vi.mock('../../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (s: string, values?: Record<string, string | number>) =>
      (values ? Object.entries(values).reduce((acc, [k, v]) => acc.replace(`{${k}}`, String(v)), s) : s),
  }),
}));

const value = (prefix: string, i: number) => `${prefix}-${i}`;

const renderPanel = (props: Partial<Parameters<typeof EndpointDetailPanel>[0]> = {}) => render(
  <ThemeProvider theme={createTheme()}>
    <EndpointDetailPanel
      simulationId="sim-1"
      endpointLabel="CORP-HOST"
      findingsLoading={false}
      findingGroups={[]}
      executions={[]}
      highlightedExecutionIds={new Set()}
      registerRow={vi.fn()}
      onSelectExecution={vi.fn()}
      execStatusLabel={(status?: string) => status ?? '-'}
      onClose={vi.fn()}
      {...props}
    />
  </ThemeProvider>,
);

describe('EndpointDetailPanel findings pagination', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('gives a paginated group its own pager, on the group header row', () => {
    // 13 files (two pages of 10) and 2 ports (one page): only the file group is paginated, and its
    // pager belongs to that group's header — under the last value it sat between two groups and read
    // as paging the whole section.
    renderPanel({
      findingGroups: [
        {
          type: 'file',
          values: Array.from({ length: 13 }, (_, i) => value('file', i)),
        },
        {
          type: 'port',
          values: ['445', '3389'],
        },
      ],
    });

    const pagers = screen.getAllByRole('navigation');
    expect(pagers).toHaveLength(1);
    expect(pagers[0].getAttribute('aria-label')).toBe('Findings pagination for file');

    // The pager sits inside the same row as the group's "FILE (13)" label, not after its values.
    const header = screen.getByText('file (13)');
    expect(header.parentElement?.contains(pagers[0])).toBe(true);
  });

  it('pages that group alone, leaving the others untouched', () => {
    renderPanel({
      findingGroups: [
        {
          type: 'file',
          values: Array.from({ length: 13 }, (_, i) => value('file', i)),
        },
        {
          type: 'port',
          values: ['445', '3389'],
        },
      ],
    });

    expect(screen.getByText('file-0')).toBeDefined();
    expect(screen.queryByText('file-12')).toBeNull();

    fireEvent.click(screen.getByLabelText('Go to page 2'));

    expect(screen.getByText('file-12')).toBeDefined();
    expect(screen.queryByText('file-0')).toBeNull();
    // The other group never paged.
    expect(screen.getByText('445')).toBeDefined();
    expect(screen.getByText('3389')).toBeDefined();
  });

  it('shows no pager when every group fits on one page', () => {
    renderPanel({
      findingGroups: [{
        type: 'port',
        values: ['445', '3389'],
      }],
    });
    expect(screen.queryByRole('navigation')).toBeNull();
  });
});
