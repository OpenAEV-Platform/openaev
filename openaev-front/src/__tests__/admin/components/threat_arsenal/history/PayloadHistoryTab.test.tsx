import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadHistoryTab from '../../../../../admin/components/threat_arsenal/history/PayloadHistoryTab';
import { type PayloadContentChangeOutput } from '../../../../../utils/api-types';
import { renderInApp } from '../threatArsenalTestUtils';

const mocks = vi.hoisted(() => ({ history: vi.fn() }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ searchThreatArsenalHistory: mocks.history }));

const entry = (action: 'INSTALL' | 'UPDATE', version: string, changed: Partial<PayloadContentChangeOutput> = {}): PayloadContentChangeOutput => ({
  content_change_id: `${action}-${version}`,
  content_change_action: action,
  content_change_version: version,
  content_change_mode: 'MANUAL',
  content_change_user: 'admin',
  content_change_created_at: '2026-11-20T10:00:00Z',
  ...changed,
});

describe('PayloadHistoryTab', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given an update should open what it changed from the version before', async () => {
    // Arrange
    const update = entry('UPDATE', '2', {
      content_change_from_version: '1',
      content_change_fields: [{
        change_field: 'command_content',
        change_before: 'mimikatz v1',
        change_after: 'mimikatz v2',
      }],
    });
    mocks.history.mockResolvedValue({
      data: {
        content: [update, entry('INSTALL', '1')],
        totalElements: 2,
      },
    });
    renderInApp(<PayloadHistoryTab actionId="action-v2" />);

    // Act
    const show = await screen.findAllByRole('button', { name: 'Show the changes' });
    fireEvent.click(show[0]);

    // Assert
    expect(show).toHaveLength(1);
    expect(mocks.history).toHaveBeenCalledTimes(1);
    expect(screen.getByText('v1 → v2')).toBeTruthy();
    expect(screen.getAllByText('Command').length).toBeGreaterThan(0);
    expect(screen.getByText('Before · v1')).toBeTruthy();
    expect(screen.getByText('After · v2')).toBeTruthy();
    expect(screen.getByRole('group', { name: /Command/ })).toBeTruthy();
  });
});
