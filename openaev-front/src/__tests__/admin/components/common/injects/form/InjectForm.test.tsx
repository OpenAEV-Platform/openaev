import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createContext, type ReactNode } from 'react';
import { useFormContext } from 'react-hook-form';
import { afterEach, describe, expect, it, vi } from 'vitest';

import InjectForm from '../../../../../../admin/components/common/injects/form/InjectForm';
import { type Inject } from '../../../../../../utils/api-types';
import { type ContractElement, type InjectorContractConverted } from '../../../../../../utils/api-types-custom';

vi.mock('../../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

vi.mock('../../../../../../components/Loader', () => ({ default: () => null }));

vi.mock('../../../../../../admin/components/common/Context', () => ({
  PermissionsContext: createContext({
    permissions: { readOnly: false },
    inherited_context: 'NONE',
  }),
}));

vi.mock('@filigran/design-system', () => ({
  Button: ({ children, onClick, disabled, type, ...rest }: {
    children: ReactNode;
    onClick?: () => void;
    disabled?: boolean;
    type?: 'button' | 'submit';
    'data-testid'?: string;
  }) => (
    <button type={type ?? 'button'} onClick={onClick} disabled={disabled} data-testid={rest['data-testid']}>{children}</button>
  ),
}));

const RegisteredInput = ({ name }: { name: string }) => {
  const { register } = useFormContext();
  return <input aria-label={name} {...register(name)} />;
};

vi.mock('../../../../../../components/fields/TextFieldController', () => ({ default: ({ name }: { name: string }) => <RegisteredInput name={name} /> }));

vi.mock('../../../../../../components/fields/TagFieldController', () => ({ default: () => null }));

vi.mock('../../../../../../admin/components/common/injects/form/InjectInjectorSelector', () => ({ default: () => null }));

vi.mock('../../../../../../admin/components/common/injects/form/InjectFormSection', () => ({ default: ({ children }: { children: ReactNode }) => <div>{children}</div> }));

vi.mock('../../../../../../admin/components/common/injects/form/InjectContentForm', () => ({ default: () => <RegisteredInput name="inject_content.command" /> }));

const field = (key: string, defaultValue: string[] = []) => ({
  key,
  label: key,
  type: 'text',
  cardinality: '1',
  mandatory: false,
  defaultValue,
} as unknown as ContractElement);

const contract = (id: string, fields: ContractElement[]) => ({
  contract_id: id,
  fields,
  variables: [],
} as unknown as InjectorContractConverted['convertedContent']);

const defaultInject = {
  inject_id: '',
  inject_title: 'Title',
  inject_depends_duration: 0,
  inject_content: {},
} as unknown as Inject;

describe('InjectForm', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it.each([true, false])('given another contract (creation: %s), should keep the typed values and only the new contract arguments', async (isCreation) => {
    // Arrange
    const onSubmitInject = vi.fn(() => Promise.resolve());
    const form = (content: InjectorContractConverted['convertedContent']) => (
      <InjectForm
        handleClose={() => {}}
        isAtomic
        isCreation={isCreation}
        defaultInject={defaultInject}
        injectorContractContent={content}
        onSubmitInject={onSubmitInject}
        articlesFromExerciseOrScenario={[]}
        uriVariable=""
        variablesFromExerciseOrScenario={[]}
      />
    );
    const { rerender } = render(form(contract('contract-a', [field('command'), field('old')])));
    await waitFor(() => expect(screen.getByLabelText('inject_title')).toBeDefined());
    fireEvent.change(screen.getByLabelText('inject_title'), { target: { value: 'Typed title' } });
    fireEvent.change(screen.getByLabelText('inject_content.command'), { target: { value: 'whoami' } });

    // Act
    rerender(form(contract('contract-b', [field('command'), field('new', ['default'])])));
    fireEvent.click(screen.getByTestId('inject-form-submit-button'));

    // Assert
    await waitFor(() => expect(onSubmitInject).toHaveBeenCalled());
    expect(onSubmitInject).toHaveBeenCalledWith(expect.objectContaining({
      inject_title: 'Typed title',
      inject_injector_contract: 'contract-b',
      inject_content: {
        command: 'whoami',
        new: 'default',
      },
    }));
  });
});
