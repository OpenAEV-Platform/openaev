import { describe, expect, it } from 'vitest';

import { payloadApprovalDisplay } from '../../../../admin/components/payloads/payloadApprovalDisplay';

describe('payloadApprovalDisplay', () => {
  it('names a pending or rejected payload with the same wording as the Threat Arsenal', () => {
    expect(payloadApprovalDisplay('PENDING')).toMatchObject({
      status: 'PAYLOAD_PENDING_APPROVAL',
      shortLabel: 'Pending approval',
      label: 'Pending approval',
    });
    expect(payloadApprovalDisplay('REJECTED')).toMatchObject({
      status: 'PAYLOAD_REJECTED',
      shortLabel: 'Rejected',
      label: 'Rejected',
    });
  });

  it('names a version replaced before any decision as superseded', () => {
    expect(payloadApprovalDisplay('SUPERSEDED')).toMatchObject({
      status: 'PAYLOAD_REJECTED',
      label: 'Payload version superseded',
    });
  });

  it('says the inject fails when it runs, not that its launch is blocked', () => {
    expect(payloadApprovalDisplay('PENDING')?.tooltip).toContain('the inject fails when it runs');
    expect(payloadApprovalDisplay('REJECTED')?.tooltip).toContain('fails when it runs');
    expect(payloadApprovalDisplay('SUPERSEDED')?.tooltip).not.toContain('blocked');
  });

  it('shows nothing for an approved payload or an inject without payload', () => {
    expect(payloadApprovalDisplay('APPROVED')).toBeUndefined();
    expect(payloadApprovalDisplay(undefined)).toBeUndefined();
  });
});
