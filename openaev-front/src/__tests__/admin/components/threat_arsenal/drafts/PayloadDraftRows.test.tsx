import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadDraftRows from '../../../../../admin/components/threat_arsenal/drafts/PayloadDraftRows';
import { type PayloadDraftOutput } from '../../../../../utils/api-types';

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string, values?: Record<string, string>) => Object.entries(values ?? {})
      .reduce((text, [key, val]) => text.replace(`{${key}}`, val), value),
    nsdt: (date: string) => date,
  }),
}));

const drafts: PayloadDraftOutput[] = [
  {
    draft_id: 'd1',
    draft_source_action_id: 'a1',
    draft_source_version: '1.0.0',
    draft_rework: false,
    draft_content: {},
    draft_updated_by: 'Sam',
    draft_created_at: '2026-11-19T10:00:00Z',
    draft_updated_at: '2026-11-19T11:00:00Z',
  },
  {
    draft_id: 'd2',
    draft_source_action_id: 'a2',
    draft_source_version: '1.1.0',
    draft_rework: true,
    draft_content: {},
    draft_created_at: '2026-11-19T10:00:00Z',
    draft_updated_at: '2026-11-19T10:30:00Z',
  },
];

describe('PayloadDraftRows', () => {
  afterEach(cleanup);

  it('given drafts should name them after their version and say who saved them', () => {
    // Arrange / Act
    render(<PayloadDraftRows drafts={drafts} canEdit onEdit={vi.fn()} onDiscard={vi.fn()} />);

    // Assert
    expect(screen.getByText('Draft from v1.0.0')).toBeTruthy();
    expect(screen.getByText('v1.1.0')).toBeTruthy();
    expect(screen.getByText('Saved by Sam, 2026-11-19T11:00:00Z')).toBeTruthy();
  });

  it('given a discard should ask first, then drop the draft', async () => {
    // Arrange
    const onDiscard = vi.fn().mockResolvedValue(undefined);
    render(<PayloadDraftRows drafts={[drafts[0]]} canEdit onEdit={vi.fn()} onDiscard={onDiscard} />);

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Discard' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Discard' }));

    // Assert
    expect(screen.getByText('Discard this draft? No version changes.')).toBeTruthy();
    await waitFor(() => expect(onDiscard).toHaveBeenCalledWith(drafts[0]));
  });

  it('given a reader should show the drafts without actions', () => {
    // Arrange / Act
    render(<PayloadDraftRows drafts={drafts} canEdit={false} onEdit={vi.fn()} onDiscard={vi.fn()} />);

    // Assert
    expect(screen.queryByRole('button', { name: 'Edit draft' })).toBeNull();
  });
});
