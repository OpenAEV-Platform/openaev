import { describe, expect, it } from 'vitest';

import { isLaunchBlocked, launchBlockedLabel, payloadApprovalDisplay } from '../../../../admin/components/payloads/payloadApprovalDisplay';
import { type LaunchBlockerOutput } from '../../../../utils/api-types';

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

describe('launchBlockedLabel', () => {
  const t = (value: string, values?: Record<string, string>) => Object.entries(values ?? {})
    .reduce((text, [key, val]) => text.replace(`{${key}}`, val), value);
  const blocker = (name: string, approvalStatus: LaunchBlockerOutput['approval_status']): LaunchBlockerOutput => ({
    id: name,
    name,
    approval_status: approvalStatus,
    reason: '',
  });

  it('says why one action blocks the launch', () => {
    expect(launchBlockedLabel(t, [blocker('TEST-A', 'PENDING')])).toBe('Can\'t launch: TEST-A is pending approval');
    expect(launchBlockedLabel(t, [blocker('TEST-A', 'APPROVED')])).toBe('Can\'t launch: TEST-A changed since its approval');
  });

  it('names up to 3 actions, then "+N"', () => {
    expect(launchBlockedLabel(t, [blocker('A', 'PENDING'), blocker('B', 'REJECTED'), blocker('C', 'PENDING'), blocker('D', 'PENDING')]))
      .toBe('Can\'t launch: A is pending approval, B is rejected, C is pending approval +1');
  });

  it('gives nothing when the launch is not blocked', () => {
    expect(launchBlockedLabel(t, [])).toBeUndefined();
    expect(launchBlockedLabel(t, undefined)).toBeUndefined();
    expect(isLaunchBlocked([])).toBe(false);
    expect(isLaunchBlocked([blocker('A', 'PENDING')])).toBe(true);
  });
});
