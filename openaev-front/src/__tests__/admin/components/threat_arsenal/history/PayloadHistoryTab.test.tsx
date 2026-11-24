import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadHistoryTab from '../../../../../admin/components/threat_arsenal/history/PayloadHistoryTab';
import { aVersion, renderInApp } from '../threatArsenalTestUtils';

const mocks = vi.hoisted(() => ({
  history: vi.fn(),
  versions: vi.fn(),
  changes: vi.fn(() => Promise.resolve({
    data: [{
      change_field: 'command_content',
      change_before: 'mimikatz v1',
      change_after: 'mimikatz v2',
    }],
  })),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  fetchThreatArsenalHistory: mocks.history,
  fetchThreatArsenalVersions: mocks.versions,
  fetchThreatArsenalVersionChanges: mocks.changes,
}));

const entry = (action: 'INSTALL' | 'UPDATE', version: string) => ({
  catalog_history_id: `${action}-${version}`,
  catalog_history_action: action,
  catalog_history_version: version,
  catalog_history_mode: 'MANUAL',
  catalog_history_user: 'admin',
  catalog_history_created_at: '2026-11-20T10:00:00Z',
});

describe('PayloadHistoryTab', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given an update should open what it changed from the version before', async () => {
    // Arrange
    mocks.history.mockResolvedValue({ data: [entry('UPDATE', '2'), entry('INSTALL', '1')] });
    mocks.versions.mockResolvedValue({
      data: [
        aVersion({
          payload_id: 'payload-v2',
          payload_action_id: 'action-v2',
          payload_version: '2',
          payload_is_latest: true,
        }),
        aVersion(),
      ],
    });
    renderInApp(<PayloadHistoryTab actionId="action-v2" />);

    // Act
    const show = await screen.findAllByRole('button', { name: 'Show the changes' });
    fireEvent.click(show[0]);

    // Assert
    expect(show).toHaveLength(1);
    expect(screen.getByText('v1 → v2')).toBeTruthy();
    expect(await screen.findByText('Command')).toBeTruthy();
    expect(await screen.findByText('Before · v1')).toBeTruthy();
    expect(screen.getByText('After · v2')).toBeTruthy();
    expect(screen.getByRole('group', { name: /Command/ })).toBeTruthy();
  });
});
