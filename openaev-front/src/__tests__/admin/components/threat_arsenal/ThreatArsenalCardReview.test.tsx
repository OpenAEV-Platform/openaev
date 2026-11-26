import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalCard from '../../../../admin/components/threat_arsenal/ThreatArsenalCard';
import { type ThreatArsenalAction } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    nsd: (value: string) => `date(${value})`,
    tPick: () => 'Action name',
  }),
}));

// Every helper getter returns an empty list or map: the card reads domains and attack patterns.
vi.mock('../../../../store', () => ({ useHelper: (selector: (helper: Record<string, () => unknown>) => unknown) => selector(new Proxy({}, { get: (_target, key) => () => (String(key).endsWith('Map') ? {} : []) })) }));

vi.mock('../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));

vi.mock('../../../../components/common/marketplace/usePlatformVersion', () => ({ default: () => undefined }));

vi.mock('../../../../admin/components/threat_arsenal/approval/approvedEarlierVersion', () => ({ useApprovedEarlierVersion: () => undefined }));

vi.mock('../../../../admin/components/threat_arsenal/ThreatArsenalActionPopover', () => ({ default: () => null }));

// The card's frame: only what it is given to show matters here.
vi.mock('../../../../components/common/marketplace/MarketplaceCard', () => ({
  default: ({ info }: {
    info?: {
      label: string;
      value: string;
    }[];
  }) => (
    <dl>
      {(info ?? []).map(item => (
        <div key={item.label}>
          <dt>{item.label}</dt>
          <dd>{item.value}</dd>
        </div>
      ))}
    </dl>
  ),
}));

const action = {
  injector_contract_id: 'action-1',
  action_labels: { en: 'Action name' },
  action_author_name: 'Original Author',
  injector_contract_updated_at: '2026-11-01T10:00:00Z',
  action_payload: { payload_version: '1.1.0' },
} as unknown as ThreatArsenalAction;

const renderCard = (review: {
  lastEditedBy?: string;
  lastEditedAt?: string;
}) => render(
  <ThreatArsenalCard
    action={action}
    selected={false}
    onSelect={vi.fn()}
    onUpdate={vi.fn()}
    onDuplicate={vi.fn()}
    onDelete={vi.fn()}
    onRunTest={vi.fn()}
    disableUpdate={false}
    disableDuplicate={false}
    disableJsonExport={false}
    disableDelete={false}
    review={review}
  />,
);

describe('ThreatArsenalCard in To review: who last edited the version (US1.2 AC11)', () => {
  afterEach(() => {
    cleanup();
  });

  it('given a last editor should show them and the date of that save, not the original author', () => {
    // Arrange / Act
    renderCard({
      lastEditedBy: 'Fixer User',
      lastEditedAt: '2026-11-20T10:00:00Z',
    });

    // Assert
    expect(screen.getByText('Last edited by')).toBeTruthy();
    expect(screen.getByText('Fixer User')).toBeTruthy();
    expect(screen.getByText('date(2026-11-20T10:00:00Z)')).toBeTruthy();
    expect(screen.queryByText('Original Author')).toBeNull();
    expect(screen.queryByText(/Submitted/)).toBeNull();
  });

  it('given nobody recorded as last editor should say Unknown', () => {
    // Arrange / Act
    renderCard({});

    // Assert
    expect(screen.getByText('Unknown')).toBeTruthy();
  });
});
