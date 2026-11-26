import '@testing-library/jest-dom/vitest';

import { zodResolver } from '@hookform/resolvers/zod';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { z } from 'zod';

import ColorPickerFieldController from '../../../components/fields/ColorPickerFieldController';
import TextFieldController from '../../../components/fields/TextFieldController';
import { type TagCreateInput } from '../../../utils/api-types';
import { FORM_HEX_COLOR_REGEX } from '../../../utils/Colors';
import { zodImplement } from '../../../utils/Zod';

afterEach(cleanup);

/**
 * Reference model of a form migrated from react-final-form (the Tag form is the example):
 * - the values type is the `*Input` of `api-types.d.ts`, and the field names are its JSON properties;
 * - the Zod schema is built with `zodImplement<Input>().with(...)`, so the compiler rejects a
 *   missing, extra or wrongly typed field, then handed to `zodResolver`;
 * - the fields are the `*FieldController` of `src/components/fields`, fed by `FormProvider`;
 * - `noValidate` on the form, so the Zod messages show instead of the browser's.
 * Messages go through `t()` in a real form; the test uses the literals.
 */
const CREATE = 'Create';
const REQUIRED = 'This field is required.';
const INVALID_COLOR = 'Color must be a valid hex value, e.g. #4CAF50';

const ModelForm = ({
  onSubmit, initialValues = {
    tag_name: '',
    tag_color: '',
  },
}: {
  onSubmit: SubmitHandler<TagCreateInput>;
  initialValues?: TagCreateInput;
}) => {
  const methods = useForm<TagCreateInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<TagCreateInput>().with({
        tag_name: z.string().min(1, { message: REQUIRED }),
        tag_color: z.string().min(1, { message: REQUIRED }).regex(FORM_HEX_COLOR_REGEX, { message: INVALID_COLOR }),
      }),
    ),
    defaultValues: initialValues,
  });
  return (
    <FormProvider {...methods}>
      <form noValidate onSubmit={methods.handleSubmit(onSubmit)}>
        <TextFieldController name="tag_name" label="Value" required />
        <ColorPickerFieldController name="tag_color" label="Color" />
        <button type="submit">{CREATE}</button>
      </form>
    </FormProvider>
  );
};

describe('RHF form model', () => {
  it('blocks the submit and shows the Zod messages on an empty form', async () => {
    const onSubmit = vi.fn();
    render(<ModelForm onSubmit={onSubmit} />);
    fireEvent.click(screen.getByRole('button', { name: CREATE }));
    expect(await screen.findAllByText(REQUIRED)).toHaveLength(2);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('rejects a colour that is not #RRGGBB', async () => {
    const onSubmit = vi.fn();
    render(
      <ModelForm
        onSubmit={onSubmit}
        initialValues={{
          tag_name: 'phishing',
          tag_color: 'red',
        }}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: CREATE }));
    expect(await screen.findByText(INVALID_COLOR)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits exactly the API input properties', async () => {
    const onSubmit = vi.fn();
    render(<ModelForm onSubmit={onSubmit} />);
    fireEvent.change(screen.getByRole('textbox', { name: /Value/ }), { target: { value: 'phishing' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Color' }), { target: { value: '#112233' } });
    fireEvent.click(screen.getByRole('button', { name: CREATE }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0]).toEqual({
      tag_name: 'phishing',
      tag_color: '#112233',
    });
  });
});
