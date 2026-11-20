import { describe, expect, it } from 'vitest';

import { eventUrl, operationLabel } from '../../../../../admin/components/profile/notifications/notificationUtils';

describe('notificationUtils', () => {
  describe('eventUrl', () => {
    it('opens the Threat Arsenal for a payload event', () => {
      // Act
      const url = eventUrl({
        operation: 'REVOKED',
        resource_type: 'PAYLOAD',
        resource_id: 'payload-1',
      });

      // Assert
      expect(url).toBe('/admin/threat-arsenal');
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

    it('opens nothing for a catalogue release', () => {
      // Act
      const url = eventUrl({
        operation: 'RELEASE_RECEIVED',
        resource_type: 'CATALOG_SYNC',
        resource_id: '1.261030.0',
      });

      // Assert
      expect(url).toBeUndefined();
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
