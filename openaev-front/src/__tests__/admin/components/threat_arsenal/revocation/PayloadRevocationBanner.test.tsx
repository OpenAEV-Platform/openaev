import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import PayloadRevocationBanner from '../../../../../admin/components/threat_arsenal/revocation/PayloadRevocationBanner';
import { expectNoMuiControls } from '../../../../utils/designSystemAssertions';
import { aVersion, renderInApp } from '../threatArsenalTestUtils';

describe('PayloadRevocationBanner', () => {
  afterEach(cleanup);

  it('given a revocation in the tenant should say who revoked it, when and why', () => {
    // Act
    const { container } = renderInApp(
      <PayloadRevocationBanner version={aVersion({
        payload_revoked: true,
        payload_revoked_by: 'Jane',
        payload_revoked_at: '2026-03-04T10:00:00Z',
        payload_revocation_reason: 'Flagged by the EDR vendor',
      })}
      />,
    );

    // Assert
    expect(screen.getByText(/^Revoked in this tenant by Jane on .+/)).toBeTruthy();
    expect(screen.getByText('Reason: Flagged by the EDR vendor')).toBeTruthy();
    expectNoMuiControls(container, 'revocation banner');
  });

  it('given a revocation without a date should say who revoked it', () => {
    // Act
    renderInApp(
      <PayloadRevocationBanner version={aVersion({
        payload_revoked: true,
        payload_revoked_by: 'Jane',
      })}
      />,
    );

    // Assert
    expect(screen.getByText('Revoked in this tenant by Jane')).toBeTruthy();
  });

  it('given a revocation by Filigran should not name a user', () => {
    // Act
    renderInApp(
      <PayloadRevocationBanner version={aVersion({
        payload_revoked: true,
        payload_revoked_by: 'Jane',
        payload_revoked_by_catalogue: true,
        payload_revoked_at: '2026-03-04T10:00:00Z',
      })}
      />,
    );

    // Assert
    expect(screen.getByText(/^Revoked by Filigran on .+/)).toBeTruthy();
    expect(screen.queryByText(/Jane/)).toBeNull();
    expect(screen.queryByText(/Reason/)).toBeNull();
  });

  it('given a revocation without user nor date should only say it is revoked', () => {
    // Act
    renderInApp(<PayloadRevocationBanner version={aVersion({ payload_revoked: true })} />);

    // Assert
    expect(screen.getByText('Revoked in this tenant')).toBeTruthy();
  });
});
