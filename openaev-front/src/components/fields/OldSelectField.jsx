import { FormControl, FormHelperText, InputLabel, Select as MUISelect } from '@mui/material';
import { Field } from 'react-final-form';

const renderHelper = ({ touched, error, submitError, helperText, variant }) => {
  if (!(touched && error)) {
    return helperText;
  }
  return (
    <FormHelperText variant={variant}>
      {touched && (error || submitError)}
    </FormHelperText>
  );
};

const renderSelectField = ({
  name,
  input: { onChange, ...inputProps },
  label,
  meta: { touched, error, submitError },
  children,
  fullWidth,
  style,
  onChange: onChangePassed,
  helperText,
  InputLabelProps,
  ...others
}) => (
  <FormControl error={touched && error} fullWidth={fullWidth} style={style}>
    {others.displayEmpty ? (
      <InputLabel
        required={InputLabelProps?.required}
        shrink={true}
        htmlFor={name}
        variant={others.variant || 'standard'}
      >
        {label}
      </InputLabel>
    ) : (
      <InputLabel required={InputLabelProps?.required} htmlFor={name} variant={others.variant || 'standard'}>
        {label}
      </InputLabel>
    )}
    <MUISelect
      onChange={(event) => {
        onChange(event.target.value);
        if (typeof onChangePassed === 'function') {
          onChangePassed(event);
        }
      }}
      {...inputProps}
      {...others}
      inputProps={{
        name,
        id: name,
      }}
      style={{ height: 30 }}
    >
      {children}
    </MUISelect>
    {renderHelper({
      touched,
      error,
      submitError,
      helperText,
      variant: others.variant || 'standard',
    })}
  </FormControl>
);

/**
 * @deprecated The component use old form library react-final-form
 */
const OldSelectField = props => (
  <Field name={props.name} component={renderSelectField} {...props} />
);

export default OldSelectField;
