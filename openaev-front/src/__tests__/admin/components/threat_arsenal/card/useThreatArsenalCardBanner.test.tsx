import { cleanup, fireEvent, screen } from '@testing-library/react';
import { type FunctionComponent } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useThreatArsenalCardBanner from '../../../../../admin/components/threat_arsenal/card/useThreatArsenalCardBanner';
import { type OpenedTab } from '../../../../../admin/components/threat_arsenal/hook/useOpenedAction';
import { type ThreatArsenalAction } from '../../../../../utils/api-types';
import { anAction, aUsage, aVersion, renderInApp, someUsages } from '../threatArsenalTestUtils';

const mocks = vi.hoisted(() => ({
  versions: vi.fn(),
  usages: vi.fn(),
}));

vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));
vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  fetchThreatArsenalVersions: mocks.versions,
  fetchThreatArsenalUsages: mocks.usages,
}));

interface Props {
  action: ThreatArsenalAction;
  olderVersionsInUse: boolean;
  onShowVersions: (action: ThreatArsenalAction, tab?: OpenedTab) => void;
}

const Banner: FunctionComponent<Props> = ({ action, olderVersionsInUse, onShowVersions }) => (
  <div data-testid="banner">{useThreatArsenalCardBanner(action, olderVersionsInUse, onShowVersions)}</div>
);

describe('useThreatArsenalCardBanner', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given injects on an older version should count them and open the Versions tab ready to move them', async () => {
    // Arrange
    const action = anAction();
    mocks.versions.mockResolvedValue({
      data: [
        aVersion({
          payload_id: 'payload-v2',
          payload_action_id: action.injector_contract_id,
          payload_version: '2',
          payload_is_latest: true,
        }),
        aVersion(),
      ],
    });
    mocks.usages.mockImplementation((actionId: string) => Promise.resolve({
      data: actionId === action.injector_contract_id
        ? someUsages([])
        : someUsages([aUsage(), aUsage({ usage_id: 'usage-2' })], 1),
    }));
    const onShowVersions = vi.fn();
    renderInApp(<Banner action={action} olderVersionsInUse onShowVersions={onShowVersions} />);
    expect(screen.getByText('Injects use an older version')).toBeTruthy();

    // Act
    fireEvent.click(screen.getByText('Move'));

    // Assert
    expect(await screen.findByText('3 injects use an older version')).toBeTruthy();
    expect(onShowVersions).toHaveBeenCalledWith(action, 'move');
  });

  it('given a revoked version should say it before the injects on an older version and open the Versions tab', () => {
    // Arrange
    const action = anAction({ action_payload_revoked: true });
    const onShowVersions = vi.fn();
    renderInApp(<Banner action={action} olderVersionsInUse onShowVersions={onShowVersions} />);

    // Act
    fireEvent.click(screen.getByText('Versions'));

    // Assert
    expect(screen.getByText('Revoked: injects using it fail')).toBeTruthy();
    expect(screen.queryByText('Injects use an older version')).toBeNull();
    expect(onShowVersions).toHaveBeenCalledWith(action, 'versions');
  });

  it('given nothing to act on should show no banner', () => {
    // Act
    renderInApp(<Banner action={anAction()} olderVersionsInUse={false} onShowVersions={vi.fn()} />);

    // Assert
    expect(screen.getByTestId('banner').childElementCount).toBe(0);
  });
});
