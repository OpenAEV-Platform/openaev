import { describe, expect, it } from 'vitest';

import { displayMessage, eventUrl, opensDetails, operationLabel } from '../../../../../admin/components/profile/notifications/notificationUtils';
import { type NotificationOutput } from '../../../../../utils/api-types';

describe('notificationUtils', () => {
  describe('eventUrl', () => {
    it('opens the action of a payload event', () => {
      // Act
      const url = eventUrl({
        operation: 'REVOKED',
        resource_type: 'PAYLOAD',
        resource_id: 'payload-1',
      });

      // Assert
      expect(url).toBe('/admin/threat-arsenal?payload=payload-1');
    });

    it('opens the scenario of a payload revoked in it', () => {
      // Act
      const url = eventUrl({
        operation: 'PAYLOAD_REVOKED',
        resource_type: 'SCENARIO',
        resource_id: 'scenario-1',
      });

      // Assert
      expect(url).toBe('/admin/scenarios/scenario-1');
    });

    it('opens the available catalogue for a release', () => {
      // Act
      const url = eventUrl({
        operation: 'RELEASE_RECEIVED',
        resource_type: 'CATALOG_SYNC',
        resource_id: '1.261030.0',
      });

      // Assert
      expect(url).toBe('/admin/threat-arsenal/available');
    });
  });

  describe('displayMessage', () => {
    it('drops the resource type prefix', () => {
      // Act & Assert
      expect(displayMessage('[payload] Nmap v1.2 revoked by Filigran')).toBe('Nmap v1.2 revoked by Filigran');
    });
  });

  describe('opensDetails', () => {
    it.each([
      ['DIGEST', 'CREATE', true],
      ['LIVE', 'SYNC_BLOCKED', true],
      ['LIVE', 'RELEASE_RECEIVED', false],
    ] as const)('a %s %s notification opens details: %s', (type, operation, expected) => {
      // Act & Assert
      expect(opensDetails({
        notification_id: 'notification-1',
        notification_type: type,
        notification_content: [{ events: [{ operation }] }],
      } as NotificationOutput)).toBe(expected);
    });
  });

  describe('operationLabel', () => {
    it.each([
      ['NEW_PAYLOAD_VERSION', 'New action version available'],
      ['INSTALLED_AUTOMATICALLY', 'Installed automatically'],
      ['UNKNOWN_EVENT', 'Unknown'],
    ])('labels %s as %s', (operation, expected) => {
      // Act & Assert
      expect(operationLabel(operation)).toBe(expected);
    });
  });
});
