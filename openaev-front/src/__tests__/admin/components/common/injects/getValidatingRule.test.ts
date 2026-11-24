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

  it.each(['select', 'choice', 'dependency-select'] as const)('validates a cardinality n %s as a non-empty list of ids', (type) => {
    const rule = getValidatingRule(field(type, 'n'), t);
    expect(rule.safeParse(['A', 'AAAA']).success).toBe(true);
    expect(rule.safeParse([]).success).toBe(false);
    expect(rule.safeParse('A').success).toBe(false);
  });

  it.each(['select', 'choice', 'dependency-select'] as const)('refuses an untouched cardinality n %s with the translated Required', (type) => {
    const result = getValidatingRule(field(type, 'n'), t).safeParse(undefined);
    expect(result.success).toBe(false);
    expect(result.error?.issues[0].message).toBe('Required');
  });

  it.each(['select', 'choice', 'dependency-select'] as const)('keeps a cardinality 1 %s as a required id', (type) => {
    const rule = getValidatingRule(field(type, '1'), t);
    expect(rule.safeParse('A').success).toBe(true);
    expect(rule.safeParse('').success).toBe(false);
    expect(rule.safeParse(['A']).success).toBe(false);
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
