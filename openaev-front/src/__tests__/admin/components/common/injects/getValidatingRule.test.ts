import { describe, expect, it } from 'vitest';

import { getValidatingRule, normalizeSingleSelectValue } from '../../../../../admin/components/common/injects/utils';
import type { Translate } from '../../../../../components/i18n';
import type { ContractElement } from '../../../../../utils/api-types-custom';

const t = ((s: string) => s) as Translate;

const field = (type: ContractElement['type'], cardinality: ContractElement['cardinality'] = '1') => ({
  key: 'doc',
  label: 'doc',
  type,
  mandatory: true,
  readOnly: false,
  cardinality,
  defaultValue: '',
}) as ContractElement;

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

describe('normalizeSingleSelectValue', () => {
  it('reads a cardinality 1 select stored as a one-element array as its id, which then validates', () => {
    const select = field('select');
    const value = normalizeSingleSelectValue(select, ['template-id']);
    expect(value).toBe('template-id');
    expect(getValidatingRule(select, t).safeParse(value).success).toBe(true);
  });

  it('reads an empty array as an empty value, still required', () => {
    const select = field('select');
    const value = normalizeSingleSelectValue(select, []);
    expect(value).toBe('');
    expect(getValidatingRule(select, t).safeParse(value).success).toBe(false);
  });

  it('keeps a string, a multiple select and other field types as stored', () => {
    expect(normalizeSingleSelectValue(field('select'), 'template-id')).toBe('template-id');
    expect(normalizeSingleSelectValue(field('select', 'n'), ['a', 'b'])).toEqual(['a', 'b']);
    expect(normalizeSingleSelectValue(field('asset'), ['asset-id'])).toEqual(['asset-id']);
  });
});
