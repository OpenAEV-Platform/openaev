import { describe, expect, it } from 'vitest';

import { eventLinkState, eventUrl, operationLabel } from '../../../../../admin/components/profile/notifications/notificationUtils';

describe('notificationUtils, payload approval', () => {
  it('given a payload trigger alert should open the action of that version in the Threat Arsenal', () => {
    // Arrange
    const event = {
      operation: 'REJECTED',
      message: '[payload] Whoami v1.0.1 rejected: deletes logs',
      resource_type: 'PAYLOAD',
      resource_id: 'payload-1',
    };

    // Act / Assert
    expect(eventUrl(event)).toBe('/admin/threat-arsenal');
    expect(eventLinkState(event)).toEqual({ openPayloadId: 'payload-1' });
  });

  it('given a scenario alert about a rejected payload should open the scenario', () => {
    // Arrange / Act
    const url = eventUrl({
      operation: 'PAYLOAD_REJECTED',
      message: '[scenario] Salt Typhoon: Whoami v1.0.1 rejected (deletes logs), 2 injects can no longer run',
      resource_type: 'SCENARIO',
      resource_id: 'scenario-1',
    });

    // Assert
    expect(url).toBe('/admin/scenarios/scenario-1');
  });

  it('given a pending-approval alert sent before triggers should open the action on the Active list', () => {
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
    expect(['APPROVAL_PENDING', 'REJECTED', 'PAYLOAD_REJECTED', 'PAYLOAD_PENDING_APPROVAL'].map(operationLabel))
      .toEqual(['Pending approval', 'Rejected', 'Payload rejected', 'Payload pending approval']);
  });
});
