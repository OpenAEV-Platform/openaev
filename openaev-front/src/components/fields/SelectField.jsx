import { FormControl, FormHelperText, InputLabel, Select as MUISelect } from '@mui/material';
import { Controller } from 'react-hook-form';

const SelectField = (props) => {
  const {
    name,
    label,
    children,
    fullWidth,
    style,
    helperText,
    control,
    defaultValue,
    InputLabelProps,
    error,
    ...others
  } = props;
  return (
    <FormControl fullWidth={fullWidth} style={style} error={error}>
      {others.displayEmpty ? (
        <InputLabel
          shrink={true}
          htmlFor={name}
          variant={others.variant || 'standard'}
          required={InputLabelProps?.required}
        >
          {label}
        </InputLabel>
      ) : (
        <InputLabel htmlFor={name} variant={others.variant || 'standard'} required={InputLabelProps?.required}>
          {label}
        </InputLabel>
      )}
      <Controller
        name={name}
        id={name}
        defaultValue={defaultValue}
        control={control}
        render={({ field }) => (
          <MUISelect {...field} {...others} value={field.value ?? ''}>
            {children}
          </MUISelect>
        )}
      />
      {!!error && (
        <FormHelperText variant={others.variant} error={!!error}>
          {helperText ?? error?.message}
        </FormHelperText>
      )}
    </FormControl>
  );
};

export default SelectField;
