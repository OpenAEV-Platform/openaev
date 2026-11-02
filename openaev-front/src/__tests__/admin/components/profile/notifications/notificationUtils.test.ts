import { describe, expect, it } from 'vitest';

import { eventUrl, operationLabel } from '../../../../../admin/components/profile/notifications/notificationUtils';

describe('notificationUtils, payload approval', () => {
  it('links a payload approval event to the action drawer in the Threat Arsenal', () => {
    // Arrange / Act
    const url = eventUrl({
      operation: 'APPROVAL_PENDING',
      message: 'Payload "Whoami" is pending approval',
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
