import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  IconButton,
} from '@filigran/design-system';
import { AddOutlined } from '@mui/icons-material';
import { Field } from 'react-final-form';

const renderAutocomplete = ({
  label,
  placeholder,
  input: { onChange, value, onBlur, name },
  meta: { touched, invalid, error },
  style,
  openCreate,
  options = [],
  multiple = false,
  freeSolo = false,
  disableClearable = false,
  disabled = false,
  onKeyDown,
  renderOption,
}) => {
  // react-final-form represents an empty field as '' while the field expects
  // null (single) or an array (multiple).
  let normalizedValue = value;
  if (multiple) {
    normalizedValue = Array.isArray(value) ? value : [];
  } else if (value === '' || value === undefined) {
    normalizedValue = null;
  }
  return (
    <div style={style}>
      <Combobox
        multiple={multiple}
        disabled={disabled}
        options={options}
        value={normalizedValue}
        selectOnFocus
        clearable={!disableClearable}
        allowCustomValue={freeSolo}
        createValueFromInput={freeSolo ? input => input : undefined}
        getOptionLabel={option => (typeof option === 'string' ? option : option?.label ?? '')}
        isOptionEqualToValue={(option, val) => option?.id === val?.id}
        error={touched && invalid}
        onInputChange={(inputValue, meta) => {
          if (freeSolo && meta.cause === 'type') {
            onChange(inputValue);
          }
        }}
        onValueChange={(newValue) => {
          onChange(newValue);
        }}
        renderOption={renderOption}
      >
        <ComboboxLabel>{label}</ComboboxLabel>
        <ComboboxField
          adornment={typeof openCreate === 'function'
            ? (
                <IconButton
                  size="sm"
                  priority="tertiary"
                  onClick={() => openCreate()}
                  aria-label={label}
                  icon={<AddOutlined fontSize="small" />}
                />
              )
            : undefined}
        >
          {multiple && <ComboboxChips />}
          <ComboboxInput
            name={name}
            placeholder={placeholder}
            onBlur={onBlur}
            onKeyDown={onKeyDown}
          />
          <ComboboxControls>
            {!disableClearable && <ComboboxClear />}
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent />
        {touched && error ? <ComboboxHelperText>{error}</ComboboxHelperText> : null}
      </Combobox>
    </div>
  );
};

/**
 * @deprecated The component use old form library react-final-form
 */
const Autocomplete = (props) => {
  return (<Field name={props.name} component={renderAutocomplete} {...props} />
  );
};

export default Autocomplete;
