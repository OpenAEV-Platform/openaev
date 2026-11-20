import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import CatalogHistoryTable from '../../../../components/common/catalog_history/CatalogHistoryTable';
import { type CatalogHistoryOutput } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (s: string) => s,
    nsdt: (date: string) => date,
  }),
}));

const entry = (overrides: Partial<CatalogHistoryOutput>): CatalogHistoryOutput => ({
  catalog_history_id: 'history-1',
  catalog_history_action: 'INSTALL',
  catalog_history_mode: 'MANUAL',
  catalog_history_version: '1.0.0',
  catalog_history_user: 'Jane',
  catalog_history_created_at: '2026-11-20T10:00:00Z',
  ...overrides,
});

describe('CatalogHistoryTable', () => {
  afterEach(cleanup);

  it('given_loadingHistory_should_showNoTable', () => {
    // Arrange & Act
    render(<CatalogHistoryTable history={null} />);

    // Assert
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByText('No history yet')).toBeNull();
  });

  it('given_noHistory_should_sayItIsEmpty', () => {
    // Arrange & Act
    render(<CatalogHistoryTable history={[]} />);

    // Assert
    expect(screen.getByText('No history yet')).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('given_changes_should_showWhatWhichVersionWhoAndWhen', () => {
    // Arrange
    const history = [
      entry({
        catalog_history_id: 'history-6',
        catalog_history_action: 'ROLLBACK',
        catalog_history_version: '1.0.0',
      }),
      entry({
        catalog_history_id: 'history-5',
        catalog_history_action: 'UPDATE',
        catalog_history_version: '1.1.0',
      }),
      entry({
        catalog_history_id: 'history-4',
        catalog_history_action: 'LIFT_REVOCATION',
        catalog_history_version: '1.1.0',
      }),
      entry({
        catalog_history_id: 'history-3',
        catalog_history_action: 'REVOKE',
        catalog_history_mode: 'AUTO',
        catalog_history_user: undefined,
        catalog_history_version: '1.1.0',
      }),
      entry({
        catalog_history_id: 'history-2',
        catalog_history_action: 'UNINSTALL',
        catalog_history_version: '1.1.0',
      }),
      entry({
        catalog_history_mode: 'AUTO',
        catalog_history_user: undefined,
      }),
    ];

    // Act
    render(<CatalogHistoryTable history={history} />);

    // Assert
    const rows = within(screen.getByRole('table', { name: 'History' })).getAllByRole('row').slice(1);
    expect(rows.map(row => within(row).getAllByRole('cell').map(cell => cell.textContent))).toEqual([
      ['Rolled back', 'v1.0.0', 'Jane', '2026-11-20T10:00:00Z'],
      ['Updated', 'v1.1.0', 'Jane', '2026-11-20T10:00:00Z'],
      ['Revocation lifted', 'v1.1.0', 'Jane', '2026-11-20T10:00:00Z'],
      ['Revoked', 'v1.1.0', 'Automatic', '2026-11-20T10:00:00Z'],
      ['Uninstalled', 'v1.1.0', 'Jane', '2026-11-20T10:00:00Z'],
      ['Installed', 'v1.0.0', 'Automatic', '2026-11-20T10:00:00Z'],
    ]);
  });
});
