import { AddOutlined } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, IconButton, TextField } from '@mui/material';
import { Field } from 'react-final-form';

const renderAutocomplete = ({
  label,
  placeholder,
  input: { onChange, ...inputProps },
  meta: { touched, invalid, error },
  fullWidth,
  style,
  openCreate,
  InputLabelProps,
  ...others
}) => {
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
        onInputChange={(_event, value) => {
          if (others.freeSolo) {
            onChange(value);
          }
        }}
        onChange={(_event, value) => {
          onChange(value);
        }}
        {...inputProps}
        {...others}
        isOptionEqualToValue={(option, value) => value === undefined || value === '' || option?.id === value?.id}
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
                          right: '35px',
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
