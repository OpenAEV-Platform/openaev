import { beforeEach, describe, expect, it, vi } from 'vitest';

import notifySubmittedForApproval from '../../../../../admin/components/threat_arsenal/approval/notifySubmittedForApproval';
import { type ThreatArsenalAction } from '../../../../../utils/api-types';
import type * as EnvironmentModule from '../../../../../utils/Environment';

const { mockNotifySuccess, mockHasApprovedEarlierVersion } = vi.hoisted(() => ({
  mockNotifySuccess: vi.fn(),
  mockHasApprovedEarlierVersion: vi.fn(),
}));

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

vi.mock('../../../../../admin/components/threat_arsenal/approval/approvedEarlierVersion', () => ({ hasApprovedEarlierVersion: mockHasApprovedEarlierVersion }));

const t = (value: string) => value;
const withPayload = (payload: object) => ({
  injector_contract_id: 'action-2',
  action_payload: payload,
} as unknown as ThreatArsenalAction);
const notifiedText = () => JSON.stringify(mockNotifySuccess.mock.calls[0][0].props.children);

describe('notifySubmittedForApproval', () => {
  beforeEach(() => {
    mockNotifySuccess.mockReset();
    mockHasApprovedEarlierVersion.mockReset();
  });

  it('tells the author a new version waits while the approved one keeps running', async () => {
    // Arrange
    mockHasApprovedEarlierVersion.mockResolvedValue(true);

    // Act
    await notifySubmittedForApproval(t, withPayload({ payload_approval_status: 'PENDING' }));

    // Assert
    expect(mockHasApprovedEarlierVersion).toHaveBeenCalledWith('action-2');
    expect(mockNotifySuccess).toHaveBeenCalledTimes(1);
    expect(notifiedText()).toContain('Submitted for approval as a new version');
  });

  it('tells the author a new action cannot be used until it is approved', async () => {
    // Arrange
    mockHasApprovedEarlierVersion.mockResolvedValue(false);

    // Act
    await notifySubmittedForApproval(t, withPayload({ payload_approval_status: 'PENDING' }));

    // Assert
    expect(notifiedText()).toContain('It can be used once an approver approves it.');
  });

  it('treats a second save of a never-approved action as a new action', async () => {
    // Arrange: two versions, the first superseded, none approved
    mockHasApprovedEarlierVersion.mockResolvedValue(false);

    // Act
    await notifySubmittedForApproval(t, withPayload({
      payload_approval_status: 'PENDING',
      payload_installed_versions: 2,
    }));

    // Assert
    expect(notifiedText()).not.toContain('as a new version');
  });

  it('says nothing when the save needed no approval', async () => {
    // Arrange / Act
    await notifySubmittedForApproval(t, withPayload({ payload_approval_status: 'APPROVED' }));

    // Assert
    expect(mockNotifySuccess).not.toHaveBeenCalled();
    expect(mockHasApprovedEarlierVersion).not.toHaveBeenCalled();
  });
});
