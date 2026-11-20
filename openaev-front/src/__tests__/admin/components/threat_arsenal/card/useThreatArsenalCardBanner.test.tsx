import { cleanup, fireEvent, screen } from '@testing-library/react';
import { type FunctionComponent } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useThreatArsenalCardBanner from '../../../../../admin/components/threat_arsenal/card/useThreatArsenalCardBanner';
import { type ThreatArsenalAction } from '../../../../../utils/api-types';
import { anAction, renderInApp } from '../threatArsenalTestUtils';

vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));

interface Props {
  action: ThreatArsenalAction;
  olderVersionsInUse: boolean;
  onShowVersions: (action: ThreatArsenalAction) => void;
}

const Banner: FunctionComponent<Props> = ({ action, olderVersionsInUse, onShowVersions }) => (
  <div data-testid="banner">{useThreatArsenalCardBanner(action, olderVersionsInUse, onShowVersions)}</div>
);

describe('useThreatArsenalCardBanner', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given injects on an older version should offer to move them, without a count', () => {
    // Arrange
    const action = anAction();
    const onShowVersions = vi.fn();
    renderInApp(<Banner action={action} olderVersionsInUse onShowVersions={onShowVersions} />);

    // Act
    fireEvent.click(screen.getByText('Move'));

    // Assert
    expect(screen.getByText('Injects use an older version')).toBeTruthy();
    expect(onShowVersions).toHaveBeenCalledWith(action);
  });

  it('given a revoked version should say it before the injects on an older version', () => {
    // Act
    renderInApp(<Banner action={anAction({ action_payload_revoked: true })} olderVersionsInUse onShowVersions={vi.fn()} />);

    // Assert
    expect(screen.getByText('Revoked: injects using it fail')).toBeTruthy();
    expect(screen.queryByText('Injects use an older version')).toBeNull();
  });

  it('given nothing to act on should show no banner', () => {
    // Act
    renderInApp(<Banner action={anAction()} olderVersionsInUse={false} onShowVersions={vi.fn()} />);

    // Assert
    expect(screen.getByTestId('banner').childElementCount).toBe(0);
  });
});
