import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import RejectedVersionPanel from '../../../../../admin/components/threat_arsenal/approval/RejectedVersionPanel';
import { type PayloadVersionOutput } from '../../../../../utils/api-types';

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string, values?: Record<string, string>) => Object.entries(values ?? {})
      .reduce((text, [key, val]) => text.replace(`{${key}}`, val), value),
    nsdt: (value: string) => value,
  }),
}));

vi.mock('../../../../../admin/components/threat_arsenal/approval/useVersionDetails', () => ({
  default: () => ({
    history: [{
      approval_status: 'REJECTED',
      approval_actor_name: 'Approver User',
      approval_created_at: '2026-11-21T10:00:00Z',
      approval_comment: 'Deletes system logs',
    }],
  }),
}));

vi.mock('../../../../../admin/components/threat_arsenal/approval/useVersionChanges', () => ({ default: () => null }));

vi.mock('../../../../../admin/components/threat_arsenal/approval/PayloadVersionComparison', () => ({ default: () => null }));

const rejected = (lastEditor?: string) => ({
  payload_action_id: 'action-1',
  payload_version: '1.1.0',
  payload_approval_status: 'REJECTED',
  payload_last_modified_by_name: lastEditor,
} as unknown as PayloadVersionOutput);

describe('RejectedVersionPanel: who last edited the version (US1.2 AC11)', () => {
  afterEach(() => {
    cleanup();
  });

  it('given a rejected version should name its last editor and who rejected it', () => {
    // Arrange / Act
    render(<RejectedVersionPanel version={rejected('Fixer User')} />);

    // Assert
    expect(screen.getByText(/Last edited by Fixer User · Rejected by Approver User/)).toBeTruthy();
    expect(screen.queryByText(/Submitted/)).toBeNull();
  });

  it('given nobody recorded as last editor should say Unknown', () => {
    // Arrange / Act
    render(<RejectedVersionPanel version={rejected()} />);

    // Assert
    expect(screen.getByText(/Last edited by Unknown/)).toBeTruthy();
  });
});
