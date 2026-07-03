import { describe, expect, it } from 'vitest';

import {
  buildContractDefaults,
  getContractFieldDefaultValue,
} from '../../../../../../admin/components/chaining/logic/drawer/ConfigureActionDetail.utils';
import type { ContractElement } from '../../../../../../utils/api-types-custom';

const makeField = (overrides: Partial<ContractElement>): ContractElement => ({
  key: 'field',
  mandatory: false,
  type: 'text',
  label: 'Field',
  readOnly: false,
  cardinality: '1',
  defaultValue: null,
  ...overrides,
});

describe('ConfigureActionDetail defaults', () => {
  it('returns predefined expectations when expectation field has no default value', () => {
    const expectations = [{
      expectation_type: 'MANUAL',
      expectation_name: 'Validate result',
      expectation_score: 100,
      expectation_expectation_group: false,
      expectation_expiration_time: 0,
    }];
    const field = makeField({
      key: 'expectations',
      type: 'expectation',
      predefinedExpectations: expectations,
    });

    expect(getContractFieldDefaultValue(field)).toEqual(expectations);
  });

  it('builds defaults with classic defaults and expectations defaults', () => {
    const expectations = [{
      expectation_type: 'DETECTION',
      expectation_name: 'Detect attack',
      expectation_score: 50,
      expectation_expectation_group: false,
      expectation_expiration_time: 3600,
    }];
    const defaults = buildContractDefaults([
      makeField({
        key: 'subject',
        type: 'text',
        defaultValue: 'Initial subject',
      }),
      makeField({
        key: 'expectations',
        type: 'expectation',
        predefinedExpectations: expectations,
      }),
      makeField({
        key: 'description',
        type: 'textarea',
      }),
    ]);

    expect(defaults).toEqual({
      subject: 'Initial subject',
      expectations,
    });
  });
});
