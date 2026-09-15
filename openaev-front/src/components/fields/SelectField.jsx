import {
  Select,
  SelectContent,
  SelectHelperText,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { Controller } from 'react-hook-form';

import { hasEmptyOption, toFieldValue, toItemValue, toSelectItems } from './selectChildren';

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
  const emptyOption = hasEmptyOption(children);

  return (
    <div style={wrapperStyle}>
      <Controller
        name={name}
        defaultValue={defaultValue}
        control={control}
        render={({ field }) => (
          <Select
            // An empty-valued option travels under a sentinel (Radix forbids
            // `value=""`), so the field value is translated on both edges — and
            // only for lists that actually carry one, otherwise an empty value
            // must keep showing the placeholder.
            value={emptyOption ? toItemValue(field.value) : (field.value ?? '')}
            onValueChange={value => field.onChange(emptyOption ? toFieldValue(value) : value)}
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
