import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { FormProvider, useForm } from 'react-hook-form';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ColorPickerFieldController from '../../../components/fields/ColorPickerFieldController';

afterEach(cleanup);

const SAVE = 'Save';

interface Values { tag_color: string }

const Harness = ({ onValues }: { onValues: (values: Values) => void }) => {
  const methods = useForm<Values>({ defaultValues: { tag_color: '#4caf50' } });
  return (
    <FormProvider {...methods}>
      <form onSubmit={methods.handleSubmit(onValues)}>
        <ColorPickerFieldController name="tag_color" label="Color" />
        <button type="submit">{SAVE}</button>
      </form>
    </FormProvider>
  );
};

describe('ColorPickerFieldController', () => {
  it('reads the colour from the form context and writes the typed one back', async () => {
    const onValues = vi.fn();
    render(<Harness onValues={onValues} />);
    const input = screen.getByRole('textbox', { name: 'Color' });
    expect(input).toHaveValue('#4caf50');
    fireEvent.change(input, { target: { value: '#112233' } });
    fireEvent.click(screen.getByRole('button', { name: SAVE }));
    await waitFor(() => expect(onValues.mock.calls[0][0]).toEqual({ tag_color: '#112233' }));
  });
});
