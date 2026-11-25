import { cleanup, fireEvent, screen } from '@testing-library/react';
import { type FunctionComponent } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useThreatArsenalCardBanner from '../../../../../admin/components/threat_arsenal/card/useThreatArsenalCardBanner';
import { type OpenedTab } from '../../../../../admin/components/threat_arsenal/hook/useOpenedAction';
import { type ThreatArsenalAction } from '../../../../../utils/api-types';
import { anAction, renderInApp } from '../threatArsenalTestUtils';

vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));

interface Props {
  action: ThreatArsenalAction;
  olderUsages: number;
  onShowVersions: (action: ThreatArsenalAction, tab?: OpenedTab) => void;
}

const Banner: FunctionComponent<Props> = ({ action, olderUsages, onShowVersions }) => (
  <div data-testid="banner">{useThreatArsenalCardBanner(action, olderUsages, onShowVersions)}</div>
);

describe('useThreatArsenalCardBanner', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given injects on an older version should count them and open the Versions tab ready to move them', () => {
    // Arrange
    const action = anAction();
    const onShowVersions = vi.fn();
    renderInApp(<Banner action={action} olderUsages={3} onShowVersions={onShowVersions} />);

    // Act
    fireEvent.click(screen.getByText('Move'));

    // Assert
    expect(screen.getByText('3 injects use an older version')).toBeTruthy();
    expect(onShowVersions).toHaveBeenCalledWith(action, 'move');
  });

  it('given a revoked version should say it before the injects on an older version and open the Versions tab', () => {
    // Arrange
    const action = anAction({ action_payload_revoked: true });
    const onShowVersions = vi.fn();
    renderInApp(<Banner action={action} olderUsages={3} onShowVersions={onShowVersions} />);

    // Act
    fireEvent.click(screen.getByText('Versions'));

    // Assert
    expect(screen.getByText('Revoked: injects using it fail')).toBeTruthy();
    expect(screen.queryByText('3 injects use an older version')).toBeNull();
    expect(onShowVersions).toHaveBeenCalledWith(action, 'versions');
  });

  it('given nothing to act on should show no banner', () => {
    // Act
    renderInApp(<Banner action={anAction()} olderUsages={0} onShowVersions={vi.fn()} />);

    // Assert
    expect(screen.getByTestId('banner').childElementCount).toBe(0);
  });
});
