import { describe, expect, it } from 'vitest';

import { payloadApprovalDisplay } from '../../../../admin/components/payloads/payloadApprovalDisplay';

describe('payloadApprovalDisplay', () => {
  it('names a pending or rejected payload with a display-only status', () => {
    expect(payloadApprovalDisplay('PENDING')).toMatchObject({
      status: 'PAYLOAD_PENDING_APPROVAL',
      label: 'Payload pending approval',
    });
    expect(payloadApprovalDisplay('REJECTED')).toMatchObject({
      status: 'PAYLOAD_REJECTED',
      label: 'Payload rejected',
    });
  });

  it('shows nothing for an approved payload or an inject without payload', () => {
    expect(payloadApprovalDisplay('APPROVED')).toBeUndefined();
    expect(payloadApprovalDisplay(undefined)).toBeUndefined();
  });
});
