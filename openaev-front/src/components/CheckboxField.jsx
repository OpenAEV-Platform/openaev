import { Checkbox } from '@filigran/design-system';
import { FormControl, FormHelperText } from '@mui/material';
import { Field } from 'react-final-form';

const renderCheckbox = ({
  label,
  input,
  meta: { touched, invalid, error, submitError },
  style,
  disabled,
  ...others
}) => (
  <FormControl error={touched && invalid}>
    <div style={{
      ...style,
      marginLeft: 5,
    }}
    >
      <Checkbox
        checked={!!input.value}
        onCheckedChange={checked => input.onChange(checked === true)}
        disabled={disabled}
        label={label}
        {...others}
      />
    </div>
    <FormHelperText>{touched && (error || submitError)}</FormHelperText>
  </FormControl>
);

/**
 * @deprecated The component use old form libnary react-final-form
 */
const CheckboxField = props => (
  <Field name={props.name} component={renderCheckbox} {...props} />
);

export default CheckboxField;
