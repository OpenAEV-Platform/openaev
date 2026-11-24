import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalListRow from '../../../../admin/components/threat_arsenal/ThreatArsenalListRow';
import { type ThreatArsenalAction } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string, values?: Record<string, string>) => Object.entries(values ?? {})
      .reduce((text, [key, val]) => text.replace(`{${key}}`, val), value),
    tPick: () => 'Dump credentials',
    nsdt: () => '24/11/2026',
  }),
}));

vi.mock('../../../../admin/components/threat_arsenal/approval/approvedEarlierVersion', () => ({ useApprovedEarlierVersion: () => false }));
vi.mock('../../../../admin/components/threat_arsenal/ThreatArsenalActionPopover', () => ({ default: () => null }));
vi.mock('../../../../admin/components/threat_arsenal/ThreatArsenalStatusChip', () => ({ default: () => null }));
vi.mock('../../../../admin/components/threat_arsenal/PayloadVerificationTag', () => ({ default: () => null }));
vi.mock('../../../../components/ItemDomains', () => ({ default: () => null }));
vi.mock('../../../../components/ItemTags', () => ({ default: () => null }));
vi.mock('../../../../components/PlatformIcon', () => ({ default: () => null }));
vi.mock('../../../../admin/components/common/injects/InjectIcon', () => ({ default: () => null }));

const action = (drafts: number) => ({
  injector_contract_id: 'action-1',
  action_labels: { en: 'Dump credentials' },
  action_platforms: ['Linux'],
  action_payload: {
    payload_id: 'payload-1',
    payload_approval_status: 'APPROVED',
    payload_drafts_count: drafts,
  },
} as unknown as ThreatArsenalAction);

const renderRow = (drafts: number) => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <ThreatArsenalListRow
        action={action(drafts)}
        checked={false}
        onSelect={vi.fn()}
        onToggleEntity={vi.fn()}
        onUpdate={vi.fn()}
        onDuplicate={vi.fn()}
        onDelete={vi.fn()}
        disableUpdate={false}
        disableDuplicate={false}
        disableJsonExport={false}
        disableDelete={false}
      />
    </TooltipProvider>
  </ThemeProvider>,
);

describe('ThreatArsenalListRow: payload drafts', () => {
  afterEach(() => cleanup());

  it('given an action with a draft should show a Draft chip on its row', () => {
    // Act
    renderRow(1);

    // Assert
    expect(screen.getByText('Draft')).toBeTruthy();
  });

  it('given an action with several drafts should count them', () => {
    // Act
    renderRow(2);

    // Assert
    expect(screen.getByText('2 drafts')).toBeTruthy();
  });

  it('given an action without draft should show no Draft chip', () => {
    // Act
    renderRow(0);

    // Assert
    expect(screen.queryByText('Draft')).toBeNull();
  });
});
