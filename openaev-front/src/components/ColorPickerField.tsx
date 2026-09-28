import { ColorPicker } from '@filigran/design-system';
import { type Control, type FieldPath, type FieldValues, useController } from 'react-hook-form';

interface Props<TFieldValues extends FieldValues = FieldValues> {
  control: Control<TFieldValues>;
  name: FieldPath<TFieldValues>;
  label?: string;
  required?: boolean;
  disabled?: boolean;
  placeholder?: string;
  helperText?: string;
  className?: string;
}

/**
 * The product's colour field, over the library ColorPicker: the component is
 * controlled and form-library-agnostic, so the react-hook-form binding lives
 * here and nowhere else. The value stays the `#RRGGBB` string every caller
 * persists - alpha is off, which is what keeps that contract.
 */
const ColorPickerField = <TFieldValues extends FieldValues = FieldValues>(
  { control, name, label, required, disabled, placeholder, helperText, className }: Props<TFieldValues>,
) => {
  const { field, fieldState } = useController({
    name,
    control,
  });

  return (
    <ColorPicker
      name={name}
      label={label}
      required={required}
      disabled={disabled}
      placeholder={placeholder}
      helperText={helperText}
      error={fieldState.error?.message}
      value={field.value || ''}
      onValueChange={field.onChange}
      // Three of the forms validate on touch: without the blur they never touch.
      onBlur={field.onBlur}
      className={className ?? 'w-full'}
    />
  );
};

export default ColorPickerField;
