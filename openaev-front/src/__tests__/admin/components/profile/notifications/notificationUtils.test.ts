import { describe, expect, it } from 'vitest';

import { eventUrl, operationLabel } from '../../../../../admin/components/profile/notifications/notificationUtils';

describe('notificationUtils, payload approval', () => {
  it('opens a pending-approval event inside the approver\'s To review queue, on that action', () => {
    // Arrange / Act
    const url = eventUrl({
      operation: 'APPROVAL_PENDING',
      message: 'Payload "Whoami" is pending approval',
      resource_type: 'THREAT_ARSENAL',
      resource_id: 'contract-1',
    });

    // Assert
    expect(url).toBe('/admin/threat-arsenal/to-review?action=contract-1');
  });

  it('opens a decision event (approved, rejected) on the action drawer in the Threat Arsenal', () => {
    // Arrange / Act
    const url = eventUrl({
      operation: 'REJECTED',
      message: 'Your payload "Whoami" was rejected',
      resource_type: 'THREAT_ARSENAL',
      resource_id: 'contract-1',
    });

    // Assert
    expect(url).toBe('/admin/threat-arsenal?action=contract-1');
  });

  it('labels the payload approval operations', () => {
    // Arrange / Act / Assert
    expect(['APPROVAL_PENDING', 'APPROVED', 'REJECTED', 'SUPERSEDED'].map(operationLabel))
      .toEqual(['Pending approval', 'Approved', 'Rejected', 'Superseded']);
  });
});
