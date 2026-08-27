import {
  Combobox,
  ComboboxChips,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  Select,
  SelectContent,
  SelectHelperText,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { Field } from 'react-final-form';

import { toOptions, toSelectItems } from './selectChildren';

const renderSelectField = ({
  name,
  input: { onChange, value, onBlur },
  label,
  meta: { touched, error, submitError },
  children,
  style,
  onChange: onChangePassed,
  helperText,
  InputLabelProps,
  multiple = false,
  disabled = false,
  renderValue,
  fullWidth,
}) => {
  const message = touched && (error || submitError) ? (error || submitError) : helperText;
  const required = InputLabelProps?.required;
  // See SelectField: the width has to land on the wrapper, not on the trigger.
  const wrapperStyle = fullWidth
    ? {
        width: '100%',
        ...style,
      }
    : style;

  // The library Select holds one string. A multiple field becomes a Combobox —
  // the nearest component that holds a set — which adds a text filter and takes
  // nothing away. The option list is read from the same `<MenuItem>` children
  // the call sites already pass.
  if (multiple) {
    const options = toOptions(children);
    const selected = options.filter(o => (Array.isArray(value) ? value : []).includes(o.value));
    return (
      <div style={wrapperStyle}>
        <Combobox
          multiple
          options={options}
          value={selected}
          onValueChange={(next) => {
            const values = next.map(o => o.value);
            onChange(values);
            if (typeof onChangePassed === 'function') {
              onChangePassed({ target: { value: values } });
            }
          }}
          getOptionLabel={option => option.label}
          isOptionEqualToValue={(a, b) => a.value === b.value}
          disabled={disabled}
          error={!!(touched && error)}
        >
          <ComboboxLabel>
            {label}
            {required ? ' *' : ''}
          </ComboboxLabel>
          <ComboboxField>
            <ComboboxChips />
            <ComboboxInput name={name} onBlur={onBlur} />
            <ComboboxControls>
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent />
          {message ? <ComboboxHelperText>{message}</ComboboxHelperText> : null}
        </Combobox>
      </div>
    );
  }

  return (
    <div style={wrapperStyle}>
      <Select
        value={value ?? ''}
        onValueChange={(next) => {
          onChange(next);
          if (typeof onChangePassed === 'function') {
            onChangePassed({ target: { value: next } });
          }
        }}
        name={name}
        disabled={disabled}
        required={required}
        error={!!(touched && error)}
      >
        <SelectLabel required={required}>{label}</SelectLabel>
        <SelectTrigger className="w-full">
          {renderValue
            ? <span>{value ? renderValue(value) : label}</span>
            : <SelectValue placeholder={label} />}
        </SelectTrigger>
        <SelectContent>{toSelectItems(children)}</SelectContent>
        {message ? <SelectHelperText>{message}</SelectHelperText> : null}
      </Select>
    </div>
  );
};

/**
 * @deprecated The component use old form library react-final-form
 */
const OldSelectField = props => (
  <Field name={props.name} component={renderSelectField} {...props} />
);

export default OldSelectField;
