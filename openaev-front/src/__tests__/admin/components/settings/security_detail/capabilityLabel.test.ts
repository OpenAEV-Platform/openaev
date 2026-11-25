import { describe, expect, it } from 'vitest';

import capabilityLabel from '../../../../../admin/components/settings/security_detail/capabilityLabel';

const labels: Record<string, string> = { APPROVE_THREAT_ARSENALS: 'Approve content' };
const t = (key: string) => labels[key] ?? key;

describe('capabilityLabel', () => {
  it('given a translated capability should show its label', () => {
    // Act / Assert
    expect(capabilityLabel(t, 'APPROVE_THREAT_ARSENALS')).toBe('Approve content');
  });

  it('given a capability without a label should show its name in words', () => {
    // Act / Assert
    expect(capabilityLabel(t, 'MANAGE_ASSESSMENT')).toBe('Manage assessment');
  });
});
