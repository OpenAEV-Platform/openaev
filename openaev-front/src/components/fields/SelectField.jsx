import {
  Select,
  SelectContent,
  SelectHelperText,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { Controller } from 'react-hook-form';

import { toSelectItems } from './selectChildren';

const SelectField = (props) => {
  const {
    name,
    label,
    children,
    style,
    helperText,
    control,
    defaultValue,
    InputLabelProps,
    error,
    disabled,
    required,
    renderValue,
    placeholder,
    fullWidth,
  } = props;
  // `fullWidth` used to reach MUI, which applied it to the control itself. The
  // library trigger sizes to its content and its `w-full` resolves against THIS
  // wrapper, so the width has to land here or the 100% is circular: the wrapper
  // shrink-wraps the trigger and the trigger fills the wrapper.
  const wrapperStyle = fullWidth
    ? {
        width: '100%',
        ...style,
      }
    : style;
  return (
    <div style={wrapperStyle}>
      <Controller
        name={name}
        defaultValue={defaultValue}
        control={control}
        render={({ field }) => (
          <Select
            value={field.value ?? ''}
            onValueChange={field.onChange}
            name={field.name}
            disabled={disabled}
            required={required ?? InputLabelProps?.required}
            error={!!error}
          >
            <SelectLabel required={required ?? InputLabelProps?.required}>{label}</SelectLabel>
            <SelectTrigger className="w-full">
              {/* `renderValue` formatted the trigger's text. The library reads it
                  from the chosen item, so a formatter is only consulted when the
                  site actually passes one. */}
              {renderValue
                ? <span>{field.value ? renderValue(field.value) : (placeholder ?? label)}</span>
                : <SelectValue placeholder={placeholder ?? label} />}
            </SelectTrigger>
            <SelectContent>{toSelectItems(children)}</SelectContent>
            {error || helperText
              ? <SelectHelperText>{helperText ?? error?.message}</SelectHelperText>
              : null}
          </Select>
        )}
      />
    </div>
  );
};

export default SelectField;
