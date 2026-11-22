import { describe, expect, it } from 'vitest';

import { eventTypeSeverity, TRIGGER_RESOURCE_TYPES } from '../../../../../admin/components/profile/triggers/triggerUtils';

describe('eventTypeSeverity', () => {
  it('gives every event type of every trigger resource type an explicit severity', () => {
    const eventTypes = new Set(TRIGGER_RESOURCE_TYPES.flatMap(option => option.eventTypes));
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
