import { describe, expect, it } from 'vitest';

import { toThreatArsenalAction } from '../../../../admin/components/threat_arsenal/threatArsenalDeepLink';
import { type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';

describe('threatArsenalDeepLink', () => {
  it('builds the list row of a payload-based action from its detail', () => {
    // Arrange
    const full = {
      action_id: 'contract-1',
      action_labels: { en: 'Whoami' },
      action_type: 'Command',
      action_status: 'VERIFIED',
      action_approval_status: 'APPROVED',
      action_payload_id: 'payload-1',
      action_payload_version: '1.1.0',
      action_domains: ['domain-1'],
      action_updated_at: '2026-10-15T00:00:00Z',
    } as unknown as ThreatArsenalActionFullOutput;

    // Act
    const action = toThreatArsenalAction(full);

    // Assert
    expect(action.injector_contract_id).toBe('contract-1');
    expect(action.action_domains_ids).toEqual(['domain-1']);
    expect(action.action_payload?.payload_approval_status).toBe('APPROVED');
    expect(action.action_payload?.payload_id).toBe('payload-1');
    expect(action.action_payload?.payload_version).toBe('1.1.0');
  });

  it('has no payload for a built-in action', () => {
    // Arrange / Act
    const action = toThreatArsenalAction({
      action_id: 'contract-2',
      action_labels: { en: 'Manual' },
    } as unknown as ThreatArsenalActionFullOutput);

    // Assert
    expect(action.action_payload).toBeUndefined();
  });
});
