import { describe, expect, it } from 'vitest';

import { getValidatingRule } from '../../../../../admin/components/common/injects/utils';
import type { ContractElement } from '../../../../../utils/api-types-custom';

const t = (s: string) => s;

const field = (type: ContractElement['type']) => ({
  key: 'doc',
  label: 'doc',
  type,
  mandatory: true,
  readOnly: false,
  cardinality: '1',
  defaultValue: '',
}) as ContractElement;

// #6973: a payload argument of type document reaches the inject form as a field of type "document".
describe('getValidatingRule', () => {
  it('validates a document field as a document id', () => {
    const rule = getValidatingRule(field('document'), t);
    expect(rule.safeParse('document-id').success).toBe(true);
    expect(rule.safeParse('').success).toBe(false);
  });

  it('keeps a text field as a required string', () => {
    const rule = getValidatingRule(field('text'), t);
    expect(rule.safeParse('value').success).toBe(true);
    expect(rule.safeParse('').success).toBe(false);
  });
});
