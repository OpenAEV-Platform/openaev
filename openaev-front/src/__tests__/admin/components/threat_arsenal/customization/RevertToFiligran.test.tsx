import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { FormProvider, useForm, useWatch } from 'react-hook-form';
import { afterEach, describe, expect, it, vi } from 'vitest';

import FiligranValuesContext from '../../../../../admin/components/threat_arsenal/customization/FiligranValuesContext';
import RevertToFiligran from '../../../../../admin/components/threat_arsenal/customization/RevertToFiligran';
import { type ActionFormValues } from '../../../../../admin/components/threat_arsenal/utils/actionFormValues';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (message: string) => message }) }));

const CommandValue = () => <span data-testid="command">{useWatch({ name: 'command_content' })}</span>;

const Form = ({ command, children }: {
  command: string;
  children: ReactNode;
}) => {
  const methods = useForm<ActionFormValues>({ defaultValues: { command_content: command } });
  return (
    <FormProvider {...methods}>
      {children}
      <CommandValue />
    </FormProvider>
  );
};

const renderRevert = (command: string, filigranValues: ActionFormValues | null) => render(
  <FiligranValuesContext.Provider value={filigranValues}>
    <Form command={command}><RevertToFiligran fields={['COMMAND_CONTENT']} /></Form>
  </FiligranValuesContext.Provider>,
);

const revertButton = () => screen.queryByRole('button', { name: 'Revert to Filigran value' });

describe('RevertToFiligran', () => {
  afterEach(cleanup);

  it('given_aPayloadThatIsNotACopy_should_showNothing', () => {
    // Act
    renderRevert('whoami /priv', null);

    // Assert
    expect(revertButton()).not.toBeInTheDocument();
  });

  it('given_theFiligranValue_should_showNothing', () => {
    // Act
    renderRevert('whoami', { command_content: 'whoami' });

    // Assert
    expect(revertButton()).not.toBeInTheDocument();
  });

  it('given_aChangedValue_should_putFiligranValueBack', () => {
    // Arrange
    renderRevert('whoami /priv', { command_content: 'whoami' });

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Revert to Filigran value' }));

    // Assert
    expect(screen.getByTestId('command')).toHaveTextContent(/^whoami$/);
    expect(revertButton()).not.toBeInTheDocument();
  });
});
