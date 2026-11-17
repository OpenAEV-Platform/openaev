import { describe, expect, it } from 'vitest';

import { injectIconType } from '../../../../../admin/components/common/injects/injectIconType';

describe('injectIconType', () => {
  it('returns the inject type of a non-payload inject', () => {
    expect(injectIconType({
      inject_type: 'openaev_email',
      inject_injector_contract: { injector_contract_injector_type: 'openaev_email' },
    })).toBe('openaev_email');
  });

  it('falls back to the contract injector type when the inject type is not loaded yet (#7823)', () => {
    expect(injectIconType({
      inject_type: undefined,
      inject_injector_contract: { injector_contract_injector_type: 'openaev_email' },
    })).toBe('openaev_email');
  });

  it('returns the payload collector type first, then the payload type, for a payload contract', () => {
    expect(injectIconType({
      inject_type: 'openaev_implant',
      inject_injector_contract: {
        injector_contract_payload: {
          payload_collector_type: 'openaev_crowdstrike',
          payload_type: 'Command',
        },
      },
    })).toBe('openaev_crowdstrike');
    expect(injectIconType({
      inject_type: 'openaev_implant',
      inject_injector_contract: { injector_contract_payload: { payload_type: 'Command' } },
    })).toBe('Command');
  });

  it('returns undefined when nothing is known', () => {
    expect(injectIconType({})).toBeUndefined();
    expect(injectIconType({ inject_injector_contract: null })).toBeUndefined();
  });
});
