import { describe, expect, it } from 'vitest';

import { eventTypeLabel, eventTypesOf, triggerResourceTypes } from '../../../../../admin/components/profile/triggers/triggerUtils';

describe('triggerUtils', () => {
  describe('eventTypesOf', () => {
    it.each<[string, boolean, string[]]>([
      ['SCENARIO', false, ['CREATE', 'UPDATE', 'DELETE', 'SCORE_DEGRADATION']],
      ['SCENARIO', true, ['CREATE', 'UPDATE', 'DELETE', 'SCORE_DEGRADATION', 'PAYLOAD_REVOKED', 'NEW_PAYLOAD_VERSION']],
      ['PAYLOAD', true, ['CREATE', 'UPDATE', 'DELETE', 'REVOKED', 'REVOCATION_LIFTED', 'INSTALLED_AUTOMATICALLY']],
      ['CATALOG_SYNC', false, []],
      ['CATALOG_SYNC', true, ['RELEASE_RECEIVED', 'SYNC_BLOCKED']],
    ])('offers the events %s sends, catalogue preview %s', (resourceType, catalogEnabled, expected) => {
      // Act
      const eventTypes = eventTypesOf(resourceType, catalogEnabled);

      // Assert
      expect(eventTypes).toEqual(expected);
    });
  });

  describe('triggerResourceTypes', () => {
    it('offers the Filigran catalog only with the catalogue preview on', () => {
      // Act
      const off = triggerResourceTypes(false).map(option => option.value);
      const on = triggerResourceTypes(true).map(option => option.value);

      // Assert
      expect(off).not.toContain('CATALOG_SYNC');
      expect(on).toContain('CATALOG_SYNC');
      expect(on.filter(value => value !== 'CATALOG_SYNC')).toEqual(off);
    });
  });

  describe('eventTypeLabel', () => {
    it.each([
      ['CREATE', 'Create'],
      ['PAYLOAD_REVOKED', 'Payload revoked'],
      ['RELEASE_RECEIVED', 'Release received'],
    ])('labels %s as %s', (eventType, expected) => {
      // Act & Assert
      expect(eventTypeLabel(eventType)).toBe(expected);
    });
  });
});
