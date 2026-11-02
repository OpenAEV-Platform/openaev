import { beforeEach, describe, expect, it, vi } from 'vitest';

import notifySubmittedForApproval from '../../../../../admin/components/threat_arsenal/approval/notifySubmittedForApproval';
import { type ThreatArsenalAction } from '../../../../../utils/api-types';
import type * as EnvironmentModule from '../../../../../utils/Environment';

const { mockNotifySuccess } = vi.hoisted(() => ({ mockNotifySuccess: vi.fn() }));

vi.mock('../../../../../utils/Environment', async (importOriginal) => {
  const original = await importOriginal<typeof EnvironmentModule>();
  return {
    ...original,
    MESSAGING$: {
      ...original.MESSAGING$,
      notifySuccess: mockNotifySuccess,
    },
  };
});

const t = (value: string) => value;
const withPayload = (payload: object) => ({ action_payload: payload } as unknown as ThreatArsenalAction);

describe('notifySubmittedForApproval', () => {
  beforeEach(() => {
    mockNotifySuccess.mockReset();
  });

  it('tells the author a new version waits while the approved one keeps running', () => {
    // Arrange / Act
    notifySubmittedForApproval(t, withPayload({
      payload_approval_status: 'APPROVED',
      payload_pending_version: true,
    }));

    // Assert
    expect(mockNotifySuccess).toHaveBeenCalledTimes(1);
    expect(JSON.stringify(mockNotifySuccess.mock.calls[0][0].props.children)).toContain('Submitted for approval as a new version');
  });

  it('tells the author a new action cannot be used until it is approved', () => {
    // Arrange / Act
    notifySubmittedForApproval(t, withPayload({ payload_approval_status: 'PENDING' }));

    // Assert
    expect(JSON.stringify(mockNotifySuccess.mock.calls[0][0].props.children)).toContain('It can be used once an approver approves it.');
  });

  it('says nothing when the save needed no approval', () => {
    // Arrange / Act
    notifySubmittedForApproval(t, withPayload({ payload_approval_status: 'APPROVED' }));

    // Assert
    expect(mockNotifySuccess).not.toHaveBeenCalled();
  });
});
