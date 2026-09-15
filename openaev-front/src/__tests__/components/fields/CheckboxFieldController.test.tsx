import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { FormProvider, useForm } from 'react-hook-form';
import { afterEach, describe, expect, it } from 'vitest';

import CheckboxFieldController from '../../../components/fields/CheckboxFieldController';

afterEach(cleanup);

const saveLabel = 'Save';

const Harness = ({ onValues }: { onValues: (values: { flag: boolean }) => void }) => {
  const methods = useForm<{ flag: boolean }>({ defaultValues: { flag: false } });
  return (
    <FormProvider {...methods}>
      <form onSubmit={methods.handleSubmit(onValues)}>
        <CheckboxFieldController name="flag" label="Create findings" />
        <button type="submit">{saveLabel}</button>
      </form>
    </FormProvider>
  );
};

describe('CheckboxFieldController', () => {
  it('toggles the form value from the library checkbox', async () => {
    let submitted: { flag: boolean } | undefined;
    const capture = (values: { flag: boolean }) => {
      submitted = values;
    };
    render(<Harness onValues={capture} />);
    const box = screen.getByRole('checkbox', { name: 'Create findings' });
    expect(document.querySelector('.MuiCheckbox-root')).toBeNull();
    expect(box).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(box);
    expect(box).toHaveAttribute('aria-checked', 'true');
    fireEvent.click(screen.getByRole('button', { name: saveLabel }));
    await screen.findByRole('checkbox', { name: 'Create findings' });
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(submitted).toEqual({ flag: true });
  });
});
