import { describe, expect, it } from 'vitest';

import {
  buildContractDefaults,
  getPlaceholderDefault,
  mapFieldLinksToStepConditions,
  withEnabledExpectations,
} from '../../../../../../admin/components/chaining/logic/drawer/ConfigureActionDetail.utils';
import type { ActionDetailData } from '../../../../../../admin/components/chaining/logic/types';
import type { InjectExpectationOutput } from '../../../../../../utils/api-types';
import type { ContractElement } from '../../../../../../utils/api-types-custom';

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

describe('contract default values', () => {
  const field = (over: Partial<ContractElement>): ContractElement => ({
    key: 'k',
    type: 'text',
    defaultValue: ['fallback'],
    ...over,
  } as ContractElement);

  describe('getPlaceholderDefault', () => {
    it.each(['text', 'number', 'document'] as const)('returns the default of a %s field', (type) => {
      // Arrange
      const contractField = field({ type });

      // Act & Assert
      expect(getPlaceholderDefault(contractField)).toBe('fallback');
    });

    it.each([
      [{ defaultValue: [''] }],
      [{ defaultValue: undefined }],
      [{
        type: 'select',
        defaultValue: ['fast'],
      }],
    ] as [Partial<ContractElement>][])('returns nothing for %j', (over) => {
      // Arrange
      const contractField = field(over);

      // Act & Assert
      expect(getPlaceholderDefault(contractField)).toBeUndefined();
    });
  });

  describe('buildContractDefaults', () => {
    it('leaves out the defaults displayed as a placeholder', () => {
      // Arrange
      const fields = [field({ key: 'target' }), field({
        key: 'count',
        type: 'number',
        defaultValue: ['3'],
      }), field({
        key: 'doc',
        type: 'document',
        defaultValue: ['document-id'],
      })];

      // Act & Assert
      expect(buildContractDefaults(fields)).toEqual({});
    });

    it('keeps the defaults of the other field types', () => {
      // Arrange
      const fields = [field({
        key: 'mode',
        type: 'select',
        defaultValue: ['fast'],
        cardinality: '1',
      })];

      // Act & Assert
      expect(buildContractDefaults(fields)).toEqual({ mode: 'fast' });
    });
  });

  describe('withEnabledExpectations', () => {
    const expectation = (type: InjectExpectationOutput['inject_expectation_type']) => ({
      expectation_type: type,
      expectation_name: type,
      expectation_score: 100,
      expectation_expectation_group: false,
      expectation_expiration_time: 3600,
      expectation_is_predefined: true,
    });
    const expectationField = field({
      key: 'expectations',
      type: 'expectation',
      availableExpectations: [expectation('DETECTION'), expectation('LOGGING')],
    });

    it.each([
      [true, ['DETECTION', 'LOGGING']],
      [false, ['DETECTION']],
    ])('given Logging enabled: %s, keeps the %j expectations', (isLoggingEnabled, expected) => {
      // Act
      const [result] = withEnabledExpectations([expectationField], isLoggingEnabled);

      // Assert
      expect(result.availableExpectations?.map(e => e.expectation_type)).toEqual(expected);
    });

    it('leaves the other fields untouched', () => {
      // Arrange
      const textField = field({ key: 'target' });

      // Act & Assert
      expect(withEnabledExpectations([textField], false)).toEqual([textField]);
    });
  });

  it('never carries a contract default as a linked field defined value', () => {
    // Arrange: nothing typed by the user, so the content holds no value for the linked field
    const conditions = mapFieldLinksToStepConditions(data({
      inject_content: buildContractDefaults([field({ key: 'target' })]),
      inject_field_links: {
        target: {
          outputTypes: ['host'],
          localScope: false,
        },
      },
    }));

    // Assert
    expect(conditions[0].condition_value).toBeUndefined();
  });
});
