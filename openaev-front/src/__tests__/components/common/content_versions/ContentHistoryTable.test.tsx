import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ContentHistoryTable } from '../../../../components/common/content_versions/ContentVersionsTable';
import { type ContentHistoryOutput } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    fsd: (value: string) => value,
    ft: (value: string) => value,
    nsdt: (value: string) => value,
  }),
}));

const entry = (overrides: Partial<ContentHistoryOutput>): ContentHistoryOutput => ({
  content_history_id: `id-${Math.random()}`,
  content_history_version: '1.0.0',
  content_history_automatic: false,
  content_history_created_at: '2026-11-25T10:00:00Z',
  ...overrides,
} as ContentHistoryOutput);

const renderTable = (history: ContentHistoryOutput[]) => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <MemoryRouter>
        <ContentHistoryTable history={history} />
      </MemoryRouter>
    </TooltipProvider>
  </ThemeProvider>,
);

// The row of the History table whose first cell reads this action line.
const rowOf = (action: string) => screen.getByText(action).closest('tr') as HTMLElement;

describe('ContentHistoryTable: approval decisions (#9816)', () => {
  afterEach(() => {
    cleanup();
  });

  it('given an explicit approval should show it with its approver and comment', () => {
    // Arrange / Act
    renderTable([entry({
      content_history_action: 'APPROVE',
      content_history_user: 'Approver User',
      content_history_details: { comment: 'Reviewed' },
    })]);

    // Assert
    const row = rowOf('Approved');
    expect(within(row).getByText('Approver User')).toBeTruthy();
    expect(within(row).getByText('Comment: Reviewed')).toBeTruthy();
  });

  it('given a rejection should show its approver and reason', () => {
    // Arrange / Act
    renderTable([entry({
      content_history_action: 'REJECT',
      content_history_user: 'Approver User',
      content_history_details: { reason: 'Deletes system logs' },
    })]);

    // Assert
    const row = rowOf('Rejected');
    expect(within(row).getByText('Approver User')).toBeTruthy();
    expect(within(row).getByText('Reason: Deletes system logs')).toBeTruthy();
  });

  it('given a version moved back to pending should show who moved it', () => {
    // Arrange / Act
    renderTable([entry({
      content_history_action: 'REOPEN',
      content_history_user: 'Approver User',
    })]);

    // Assert
    expect(within(rowOf('Moved to pending approval')).getByText('Approver User')).toBeTruthy();
  });

  it('given an automatic approval of an approver\'s write should name its author', () => {
    // Arrange / Act
    renderTable([entry({
      content_history_action: 'APPROVE',
      content_history_automatic: true,
      content_history_user: 'Author User',
    })]);

    // Assert
    expect(within(rowOf('Auto-approved')).getByText('Author User')).toBeTruthy();
  });

  it('given an automatic approval without a user should name the platform or Filigran', () => {
    // Arrange / Act
    renderTable([
      entry({
        content_history_action: 'APPROVE',
        content_history_automatic: true,
        content_history_version: '1.0.0',
        content_history_details: { source: 'platform' },
      }),
      entry({
        content_history_action: 'APPROVE',
        content_history_automatic: true,
        content_history_version: '2.0.0',
        content_history_details: { source: 'catalog' },
      }),
    ]);

    // Assert
    const rows = screen.getAllByText('Auto-approved').map(cell => cell.closest('tr') as HTMLElement);
    expect(within(rows[0]).getByText('Platform')).toBeTruthy();
    expect(within(rows[1]).getByText('Filigran')).toBeTruthy();
  });

  it('given a version approved by the upgrade should say so and name no user', () => {
    // Arrange / Act
    renderTable([entry({
      content_history_action: 'APPROVE',
      content_history_automatic: true,
      content_history_version: '1.0.0',
      content_history_details: { source: 'upgrade' },
    })]);

    // Assert
    const row = screen.getByText('Approved when payload approval was introduced').closest('tr') as HTMLElement;
    expect(within(row).queryByText('Platform')).toBeNull();
    expect(screen.queryByText('Auto-approved')).toBeNull();
  });

  it('given a superseded version should show it', () => {
    // Arrange / Act
    renderTable([entry({
      content_history_action: 'SUPERSEDE',
      content_history_automatic: true,
      content_history_user: 'Author User',
    })]);

    // Assert
    expect(within(rowOf('Superseded')).getByText('Author User')).toBeTruthy();
  });
});
