import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PayloadTrustLine from '../../../../../admin/components/threat_arsenal/versions/PayloadTrustLine';
import { anAction, renderInApp } from '../threatArsenalTestUtils';

vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));

describe('PayloadTrustLine', () => {
  afterEach(cleanup);

  it('given a Filigran payload should show its version and source', () => {
    // Act
    renderInApp(
      <PayloadTrustLine action={anAction({
        action_payload_version: '3',
        action_payload_managed: true,
      })}
      />,
    );

    // Assert
    expect(screen.getByText('v3')).toBeTruthy();
    expect(screen.getByText('Filigran')).toBeTruthy();
  });

  it('given a local payload without version should show its source only', () => {
    // Act
    renderInApp(<PayloadTrustLine action={anAction({ action_payload_managed: false })} />);

    // Assert
    expect(screen.getByText('Local')).toBeTruthy();
    expect(screen.queryByText(/^v/)).toBeNull();
  });

  it('given an action without payload should show nothing', () => {
    // Act
    renderInApp(<PayloadTrustLine action={anAction({ action_payload: undefined })} />);

    // Assert
    expect(screen.queryByTestId('payload-trust-line')).toBeNull();
  });
});
