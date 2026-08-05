import { describe, expect, it } from 'vitest';

import { mapFieldLinksToStepConditions } from '../../../../../../admin/components/chaining/logic/drawer/ConfigureActionDetail.utils';
import type { ActionDetailData } from '../../../../../../admin/components/chaining/logic/types';

// Minimal ActionDetailData carrying only what mapFieldLinksToStepConditions reads.
const data = (over: Partial<ActionDetailData>): ActionDetailData => ({
  inject_title: 'a',
  inject_injector_contract: 'c',
  inject_assets: [],
  inject_asset_groups: [],
  inject_teams: [],
  inject_all_teams: false,
  inject_documents: [],
  inject_content: {},
  inject_field_links: {},
  contract_fields: [],
  ...over,
});

describe('mapFieldLinksToStepConditions', () => {
  it(`carries a linked field's defined value into condition_value alongside its type`, () => {
    // The field "target" is linked to the "host" output type AND has a defined value typed in.
    const conditions = mapFieldLinksToStepConditions(data({
      inject_content: { target: '10.0.0.5' },
      inject_field_links: {
        target: {
          outputTypes: ['host'],
          localScope: false,
        },
      },
    }));

    expect(conditions).toHaveLength(1);
    expect(conditions[0]).toMatchObject({
      condition_type: 'MAPPER',
      condition_key: 'target',
      condition_key_types: ['host'],
      condition_value: '10.0.0.5', // the defined value is NOT dropped once a type is linked
      condition_mapping_type: 'GLOBAL',
    });
  });

  it('omits condition_value when the field has no defined value', () => {
    const conditions = mapFieldLinksToStepConditions(data({
      inject_content: {},
      inject_field_links: {
        target: {
          outputTypes: ['host'],
          localScope: true,
        },
      },
    }));

    expect(conditions[0].condition_value).toBeUndefined();
    expect(conditions[0].condition_mapping_type).toBe('LOCAL');
  });

  it('falls back to a text key type for a link that carries no output type', () => {
    // Degenerate case: a link with no output types must never serialize empty keyTypes, or the
    // backend drops the whole step's execution batches.
    const conditions = mapFieldLinksToStepConditions(data({
      inject_content: { target: 'literal' },
      inject_field_links: {
        target: {
          outputTypes: [],
          localScope: false,
        },
      },
    }));

    expect(conditions[0].condition_key_types).toEqual(['text']);
    expect(conditions[0].condition_value).toBe('literal');
  });

  it('coerces a numeric defined value to its string form', () => {
    const conditions = mapFieldLinksToStepConditions(data({
      inject_content: { port: 8080 },
      inject_field_links: {
        port: {
          outputTypes: ['port'],
          localScope: false,
        },
      },
    }));

    expect(conditions[0].condition_value).toBe('8080');
  });

  it('omits condition_value for non-scalar content (array or object)', () => {
    // A multi-cardinality select stores an array; String() would coerce it into a meaningless
    // "a,b" candidate. Only scalar values are carried into condition_value.
    const conditions = mapFieldLinksToStepConditions(data({
      inject_content: {
        multi: ['a', 'b'],
        obj: { nested: true },
      },
      inject_field_links: {
        multi: {
          outputTypes: ['text'],
          localScope: false,
        },
        obj: {
          outputTypes: ['text'],
          localScope: false,
        },
      },
    }));

    expect(conditions).toHaveLength(2);
    expect(conditions[0].condition_value).toBeUndefined();
    expect(conditions[1].condition_value).toBeUndefined();
  });

  it('produces no conditions for fields that are not linked', () => {
    const conditions = mapFieldLinksToStepConditions(data({
      inject_content: { free_text: 'kept in inject_content only' },
      inject_field_links: {},
    }));

    expect(conditions).toEqual([]);
  });
});
