import { render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ConfigureActionDetail from '../../../../../../admin/components/chaining/logic/drawer/ConfigureActionDetail';
import {
  applyExpectationDefaults,
  buildContractDefaults,
  getContractFieldDefaultValue,
  normalizeSingleCardinalityContent,
  normalizeInjectContentExpectations,
} from '../../../../../../admin/components/chaining/logic/drawer/ConfigureActionDetail.utils';
import type { ThreatArsenalAction } from '../../../../../../utils/api-types';
import type { ContractElement } from '../../../../../../utils/api-types-custom';

const { directFetchInjectorContractMock, injectExpectationsSpy } = vi.hoisted(() => ({
  directFetchInjectorContractMock: vi.fn(),
  injectExpectationsSpy: vi.fn(),
}));

vi.mock('../../../../../../actions/InjectorContracts', () => ({
  directFetchInjectorContract: directFetchInjectorContractMock,
}));

vi.mock('../../../../../../components/i18n', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...(original as Record<string, unknown>),
    useFormatter: () => ({
      t: (value: string) => value,
      tPick: (labels: Record<string, string>) => labels.en ?? Object.values(labels)[0] ?? '',
    }),
  };
});

vi.mock('../../../../../../components/common/Drawer', () => ({
  default: ({ open, children }: { open: boolean; children: ReactNode }) => (open ? <div>{children}</div> : null),
}));

vi.mock('../../../../../../components/fields/TextFieldController', () => ({
  default: ({ label }: { label: string }) => <div>{label}</div>,
}));

vi.mock('../../../../../../admin/components/common/DrawerBreadcrumb', () => ({
  default: () => <div data-testid="drawer-breadcrumb" />,
}));

vi.mock('../../../../../../admin/components/chaining/logic/drawer/InjectDataFieldItem', () => ({
  default: () => <div data-testid="inject-data-field-item" />,
}));

vi.mock('../../../../../../admin/components/common/injects/expectations/InjectExpectations', () => ({
  default: (props: {
    expectationDatas: unknown[];
    predefinedExpectations: unknown[];
    availableExpectations: unknown[];
  }) => {
    injectExpectationsSpy(props);
    return <div data-testid="inject-expectations">InjectExpectations</div>;
  },
}));

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

describe('ConfigureActionDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('renders expectations component when contract has available expectations but no initial expectation data', async () => {
    const expectation = {
      expectation_type: 'DETECTION',
      expectation_name: 'Detect attack',
      expectation_score: 100,
      expectation_expectation_group: false,
      expectation_expiration_time: 3600,
    };
    const expectationField = makeField({
      key: 'expectations',
      type: 'expectation',
      availableExpectations: [expectation],
    });
    directFetchInjectorContractMock.mockResolvedValue({
      data: {
        injector_contract_content: JSON.stringify({
          fields: [expectationField],
        }),
      },
    });

    const action = {
      injector_contract_id: 'contract-1',
      action_injector_type: 'injector-1',
      action_labels: { en: 'Action label' },
    } as unknown as ThreatArsenalAction;

    render(
      <ConfigureActionDetail
        open
        action={action}
        validAssets={[]}
        onClose={() => {}}
        onBack={() => {}}
        onBackToRoot={() => {}}
        onSave={() => {}}
      />,
    );

    await waitFor(() => expect(screen.getByTestId('inject-expectations')).toBeDefined());
    expect(injectExpectationsSpy).toHaveBeenCalledWith(expect.objectContaining({
      expectationDatas: [expectation],
      availableExpectations: [expectation],
      predefinedExpectations: [],
    }));
  });
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

  it('returns predefined expectations when expectation default value is empty array', () => {
    const expectations = [{
      expectation_type: 'DETECTION',
      expectation_name: 'Default detection',
      expectation_score: 100,
      expectation_expectation_group: false,
      expectation_expiration_time: 3600,
    }];
    const field = makeField({
      key: 'expectations',
      type: 'expectation',
      defaultValue: [],
      predefinedExpectations: expectations,
    });

    expect(getContractFieldDefaultValue(field)).toEqual(expectations);
  });

  it('returns scalar default for single cardinality non-expectation fields', () => {
    const field = makeField({
      key: 'obfuscator',
      type: 'choice',
      cardinality: '1',
      defaultValue: ['plain-text'],
    });

    expect(getContractFieldDefaultValue(field)).toEqual('plain-text');
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

  it('normalizes expectation key to expectations for chaining save payload', () => {
    const fields = [
      makeField({
        key: 'inject_expectations',
        type: 'expectation',
      }),
    ];
    const customExpectations = [{
      expectation_type: 'MANUAL',
      expectation_name: 'Custom expectation',
      expectation_score: 100,
      expectation_expectation_group: false,
      expectation_expiration_time: 0,
    }];

    expect(normalizeInjectContentExpectations(
      { inject_expectations: customExpectations },
      fields,
    )).toEqual({
      inject_expectations: customExpectations,
      expectations: customExpectations,
    });
  });

  it('restores default expectations when selected expectations are empty', () => {
    const defaultExpectations = [{
      expectation_type: 'MANUAL',
      expectation_name: 'Default expectation',
      expectation_score: 100,
      expectation_expectation_group: false,
      expectation_expiration_time: 0,
    }];
    const fields = [
      makeField({
        key: 'expectations',
        type: 'expectation',
        predefinedExpectations: defaultExpectations,
      }),
    ];

    expect(applyExpectationDefaults({ expectations: [] }, fields)).toEqual({
      expectations: defaultExpectations,
    });
  });

  it('normalizes single cardinality array values to scalar content', () => {
    const fields = [
      makeField({
        key: 'obfuscator',
        type: 'choice',
        cardinality: '1',
      }),
    ];

    expect(normalizeSingleCardinalityContent({ obfuscator: ['plain-text'] }, fields)).toEqual({
      obfuscator: 'plain-text',
    });
  });
});
