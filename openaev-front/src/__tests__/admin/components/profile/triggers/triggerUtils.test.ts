import { describe, expect, it } from 'vitest';

import { eventTypesOf } from '../../../../../admin/components/profile/triggers/triggerUtils';

describe('triggerUtils, payload approval events', () => {
  it('given the payload resource type should offer pending approval and rejected next to revoked', () => {
    // Act
    const events = eventTypesOf('PAYLOAD');

    // Assert
    expect(events).toEqual(expect.arrayContaining(['REVOKED', 'APPROVAL_PENDING', 'REJECTED']));
    expect(events).not.toContain('APPROVED');
    expect(events).not.toContain('SUPERSEDED');
  });

  it('given the scenario resource type should offer payload rejected and payload pending approval next to payload revoked', () => {
    // Act
    const events = eventTypesOf('SCENARIO');

    // Assert
    expect(events).toEqual(expect.arrayContaining(['PAYLOAD_REVOKED', 'PAYLOAD_REJECTED', 'PAYLOAD_PENDING_APPROVAL']));
  });
});
