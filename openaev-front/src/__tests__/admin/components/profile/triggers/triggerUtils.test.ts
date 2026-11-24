import { describe, expect, it } from 'vitest';

import { eventTypeLabel, eventTypeSeverity, eventTypesOf, TRIGGER_RESOURCE_TYPES, triggerResourceTypes } from '../../../../../admin/components/profile/triggers/triggerUtils';

describe('eventTypeSeverity', () => {
  it('gives every event type of every trigger resource type an explicit severity', () => {
    const eventTypes = new Set(TRIGGER_RESOURCE_TYPES.flatMap(option => eventTypesOf(option.value, true)));
    eventTypes.forEach((eventType) => {
      expect({
        eventType,
        severity: eventTypeSeverity(eventType),
      }).not.toEqual({
        eventType,
        severity: 'neutral',
      });
    });
  });

  it('treats a critical asset reached like a blocked sync', () => {
    expect(eventTypeSeverity('CRITICAL_ASSET_REACHED')).toBe('critical');
    expect(eventTypeSeverity('CRITICAL_ASSET_REACHED')).toBe(eventTypeSeverity('SYNC_BLOCKED'));
  });

  it('falls back to neutral for an unknown event type', () => {
    expect(eventTypeSeverity('UNKNOWN_EVENT')).toBe('neutral');
  });
});

describe('triggerUtils', () => {
  describe('eventTypesOf', () => {
    it.each<[string, boolean, string[]]>([
      ['SCENARIO', false, ['CREATE', 'UPDATE', 'DELETE', 'SCORE_DEGRADATION']],
      ['SCENARIO', true, ['CREATE', 'UPDATE', 'DELETE', 'SCORE_DEGRADATION', 'PAYLOAD_REVOKED', 'NEW_PAYLOAD_VERSION']],
      ['PAYLOAD', true, ['CREATE', 'UPDATE', 'DELETE', 'REVOKED', 'REVOCATION_LIFTED', 'BASE_UPDATE_AVAILABLE']],
      ['CATALOG_SYNC', false, []],
      ['CATALOG_SYNC', true, ['RELEASE_RECEIVED', 'INSTALLED_AUTOMATICALLY', 'SYNC_BLOCKED']],
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
