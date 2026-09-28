import { ColorPicker } from '@filigran/design-system';
import { Field } from 'react-final-form';

const ColorPickerFieldBase = ({
  label,
  required,
  disabled,
  placeholder,
  className,
  input: { onChange, onBlur, value, name },
  meta: { touched, invalid, error, submitError },
}) => (
  <ColorPicker
    name={name}
    label={label}
    required={required}
    disabled={disabled}
    placeholder={placeholder}
    // The library takes the message, never a boolean.
    error={touched && invalid ? (error || submitError) : undefined}
    value={value || ''}
    onValueChange={onChange}
    // The error above is gated on `touched`, which only the blur sets.
    onBlur={onBlur}
    className={className ?? 'w-full'}
  />
);

/**
 * @deprecated The component use old form libnary react-final-form
 */
const DeprecatedColorPickerField = props => (
  <Field name={props.name} component={ColorPickerFieldBase} {...props} />
);

export default DeprecatedColorPickerField;
