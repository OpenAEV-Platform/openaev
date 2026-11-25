import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ContentHistoryTable from '../../../../components/common/content_history/ContentHistoryTable';
import { type ContentHistory } from '../../../../components/common/content_history/useContentHistory';
import { type PaginationHelpers } from '../../../../components/common/queryable/pagination/PaginationHelpers';
import { type ContentChangeOutput } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (s: string) => s,
    fsd: (date: string) => `day ${date}`,
    ft: (date: string) => `time ${date}`,
  }),
}));

const entry = (overrides: Partial<ContentChangeOutput>): ContentChangeOutput => ({
  content_change_id: 'history-1',
  content_change_action: 'INSTALL',
  content_change_mode: 'MANUAL',
  content_change_version: '1.0.0',
  content_change_user: 'Jane',
  content_change_created_at: '2026-11-20T10:00:00Z',
  ...overrides,
});

const historyOf = (entries: ContentChangeOutput[] | null, total = entries?.length ?? 0): ContentHistory<ContentChangeOutput> => ({
  entries,
  pagination: {
    page: 0,
    elementsPerPage: 20,
    getTotalElements: () => total,
    handleChangePage: vi.fn(),
    handleChangeRowsPerPage: vi.fn(),
  } as unknown as PaginationHelpers,
});

describe('ContentHistoryTable', () => {
  afterEach(cleanup);

  it('given_loadingHistory_should_showNoTable', () => {
    // Arrange & Act
    render(<ContentHistoryTable history={historyOf(null)} />);

    // Assert
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByText('No history yet')).toBeNull();
  });

  it('given_noHistory_should_sayItIsEmpty', () => {
    // Arrange & Act
    render(<ContentHistoryTable history={historyOf([])} />);

    // Assert
    expect(screen.getByText('No history yet')).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('given_changes_should_showWhatWhichVersionWhoAndWhen', () => {
    // Arrange
    const history = [
      entry({
        content_change_id: 'history-6',
        content_change_action: 'ROLLBACK',
        content_change_version: '1.0.0',
      }),
      entry({
        content_change_id: 'history-5',
        content_change_action: 'UPDATE',
        content_change_version: '1.1.0',
      }),
      entry({
        content_change_id: 'history-4',
        content_change_action: 'LIFT_REVOCATION',
        content_change_version: '1.1.0',
      }),
      entry({
        content_change_id: 'history-3',
        content_change_action: 'REVOKE',
        content_change_mode: 'AUTO',
        content_change_user: undefined,
        content_change_version: '1.1.0',
      }),
      entry({
        content_change_id: 'history-2',
        content_change_action: 'UNINSTALL',
        content_change_version: '1.1.0',
      }),
      entry({
        content_change_mode: 'AUTO',
        content_change_user: undefined,
      }),
    ];

    // Act
    render(<ContentHistoryTable history={historyOf(history)} />);

    // Assert
    const rows = within(screen.getByRole('table', { name: 'History' })).getAllByRole('row').slice(1);
    const when = 'day 2026-11-20T10:00:00Ztime 2026-11-20T10:00:00Z';
    expect(rows.map(row => within(row).getAllByRole('cell').map(cell => cell.textContent))).toEqual([
      ['v1.0.0Rolled back', 'Jane', when],
      ['v1.1.0Updated', 'Jane', when],
      ['v1.1.0Revocation lifted', 'Jane', when],
      ['v1.1.0Revoked', 'Automatic', when],
      ['v1.1.0Uninstalled', 'Jane', when],
      ['v1.0.0Installed', 'Automatic', when],
    ]);
  });

  it('given_changes_should_readFromVersionToVersionWithTheFieldsAndOpenOnThem', () => {
    // Arrange
    render(
      <ContentHistoryTable
        history={historyOf([entry({
          content_change_action: 'UPDATE',
          content_change_version: '1.1.0',
        })])}
        changesOf={() => ({
          fromVersion: '1.0.0',
          summary: 'Command',
          details: 'The before / after',
        })}
      />,
    );
    const row = within(screen.getByRole('table', { name: 'History' })).getAllByRole('row')[1];

    // Act
    fireEvent.click(within(row).getByRole('button', { name: 'Show the changes' }));

    // Assert
    expect(within(row).getAllByRole('cell').map(cell => cell.textContent).slice(0, 3)).toEqual(['v1.0.0 → v1.1.0Updated', 'Command', 'Jane']);
    expect(screen.getByText('The before / after')).toBeTruthy();
  });

  it('given_onePage_should_showNoPagination', () => {
    // Arrange & Act
    render(<ContentHistoryTable history={historyOf([entry({})])} />);

    // Assert
    expect(screen.queryByText(/of 1/)).toBeNull();
  });

  it('given_moreThanAPage_should_showThePagination', () => {
    // Arrange & Act
    render(<ContentHistoryTable history={historyOf([entry({})], 45)} />);

    // Assert
    expect(screen.getByText('1–20 of 45')).toBeTruthy();
  });
});
