import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalDraftEditor from '../../../../../admin/components/threat_arsenal/drafts/ThreatArsenalDraftEditor';

const { source, mockContentChanges } = vi.hoisted(() => ({
  mockContentChanges: vi.fn(),
  source: {
    injector_contract_id: 'a1',
    action_labels: { en: 'Dump credentials' },
    action_type: 'Command',
    action_platforms: ['Linux'],
    command_executor: 'bash',
    command_content: 'echo before',
    action_arguments: [],
    action_prerequisites: [],
  },
}));

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string, values?: Record<string, string>) => Object.entries(values ?? {})
      .reduce((text, [key, val]) => text.replace(`{${key}}`, val), value),
    tPick: () => 'Dump credentials',
    nsdt: () => '24/11/2026',
  }),
}));

vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  fetchThreatArsenalAction: () => Promise.resolve({ data: source }),
  updateThreatArsenalAction: vi.fn(),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-version-actions', () => ({
  fetchThreatArsenalContentChanges: (...args: unknown[]) => mockContentChanges(...args),
  fetchThreatArsenalVersions: () => Promise.resolve({
    data: [{
      payload_action_id: 'a1',
      payload_version: '1.0.0',
      payload_installed: true,
      payload_approval_status: 'APPROVED',
    }],
  }),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-draft-actions', () => ({
  fetchThreatArsenalDrafts: () => Promise.resolve({
    data: [{
      draft_id: 'd1',
      draft_source_action_id: 'a1',
      draft_source_version: '1.0.0',
      draft_rework: false,
      draft_content: {
        ...source,
        command_content: 'echo after',
      },
      draft_created_at: '2026-11-24T10:00:00Z',
      draft_updated_at: '2026-11-24T10:00:00Z',
    }],
  }),
  createThreatArsenalDraft: vi.fn(),
  discardThreatArsenalDraft: vi.fn(),
  publishThreatArsenalDraft: vi.fn(),
  saveThreatArsenalDraft: vi.fn(),
}));

vi.mock('../../../../../admin/components/threat_arsenal/ThreatArsenalActionForm', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/threat_arsenal/utils/SnapshotRemediationProvider', () => ({ default: ({ children }: { children: ReactNode }) => <>{children}</> }));

describe('ThreatArsenalDraftEditor: what a draft changes (#9469 follow-up)', () => {
  afterEach(() => cleanup());

  it('given a draft should compare it with the version it started from', async () => {
    // Arrange
    mockContentChanges.mockResolvedValue({
      data: [{
        field: 'command_content',
        before: 'echo before',
        after: 'echo after',
      }],
    });

    // Act
    render(
      <ThemeProvider theme={createTheme()}>
        <TooltipProvider>
          <ThreatArsenalDraftEditor actionId="a1" initialSelection="draft:d1" onClose={vi.fn()} onPublished={vi.fn()} />
        </TooltipProvider>
      </ThemeProvider>,
    );

    // Assert
    expect(await screen.findByText('What this draft changes')).toBeTruthy();
    expect(screen.getByText('1 field changed: Command · compared with v1.0.0, the version this draft started from')).toBeTruthy();
    // The saved draft is sent as publishing would send it, to the version it started from.
    expect(mockContentChanges).toHaveBeenCalledWith('a1', expect.objectContaining({ command_content: 'echo after' }));
  });

  it('given a draft that only renames the action should show the Name change', async () => {
    // Arrange
    mockContentChanges.mockResolvedValue({
      data: [{
        field: 'payload_name',
        before: 'Dump credentials',
        after: 'Dump credentials v2',
      }],
    });

    // Act
    render(
      <ThemeProvider theme={createTheme()}>
        <TooltipProvider>
          <ThreatArsenalDraftEditor actionId="a1" initialSelection="draft:d1" onClose={vi.fn()} onPublished={vi.fn()} />
        </TooltipProvider>
      </ThemeProvider>,
    );

    // Assert
    expect(await screen.findByText('1 field changed: Name · compared with v1.0.0, the version this draft started from')).toBeTruthy();
  });
});
