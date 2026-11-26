import { type ComponentProps } from 'react';
import { useFormContext } from 'react-hook-form';

import ColorPickerField from '../ColorPickerField';

type Props = Omit<ComponentProps<typeof ColorPickerField>, 'control'>;

/**
 * `ColorPickerField` for a form shared through `FormProvider`: same field, the
 * control comes from the context like in the other `*FieldController`.
 */
const ColorPickerFieldController = (props: Props) => {
  const { control } = useFormContext();

  return <ColorPickerField control={control} {...props} />;
};

export default ColorPickerFieldController;
