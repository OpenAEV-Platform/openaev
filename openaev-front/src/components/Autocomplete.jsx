import { AddOutlined } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, IconButton, TextField } from '@mui/material';
import { Field } from 'react-final-form';

const renderAutocomplete = ({
  label,
  placeholder,
  input: { onChange, value, ...inputProps },
  meta: { touched, invalid, error },
  fullWidth,
  style,
  openCreate,
  InputLabelProps,
  ...others
}) => {
  // react-final-form represents an empty field as '' while MUI expects null
  // (single) or an array (multiple). Passing '' through used to be compensated
  // by an isOptionEqualToValue that returned true for empty values, which made
  // MUI mark EVERY option as selected when the field had no value.
  let normalizedValue = value;
  if (others.multiple) {
    normalizedValue = Array.isArray(value) ? value : [];
  } else if (value === '' || value === undefined) {
    normalizedValue = null;
  }
  return (
    <div style={{ position: 'relative' }}>
      <MuiAutocomplete
        label={label}
        selectOnFocus
        autoHighlight
        clearOnBlur={false}
        clearOnEscape={false}
        disableClearable
        slotProps={{ paper: { elevation: 2 } }}
        onInputChange={(_event, inputValue) => {
          if (others.freeSolo) {
            onChange(inputValue);
          }
        }}
        onChange={(_event, newValue) => {
          onChange(newValue);
        }}
        {...inputProps}
        value={normalizedValue}
        {...others}
        isOptionEqualToValue={(option, val) => option?.id === val?.id}
        renderInput={params => (
          <TextField
            {...params}
            InputLabelProps={InputLabelProps}
            variant={others.variant || 'standard'}
            label={label}
            placeholder={placeholder}
            fullWidth={fullWidth}
            style={style}
            error={touched && invalid}
            helperText={touched && error}
            InputProps={{
              ...params.InputProps,
              endAdornment: (
                <>
                  {
                    typeof openCreate === 'function' && (
                      <IconButton
                        style={{
                          position: 'absolute',
                          // Clearable fields render the MUI clear icon just left of the
                          // popup indicator, so the create button moves one slot further.
                          right: others.disableClearable === false ? '65px' : '35px',
                        }}
                        onClick={() => openCreate()}
                      >
                        <AddOutlined />
                      </IconButton>
                    )
                  }
                  {params.InputProps.endAdornment}
                </>
              ),
            }}
          />
        )}
      />
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
