import { Switch } from '@filigran/design-system';
import { FormControl, FormHelperText } from '@mui/material';
import { Field } from 'react-final-form';

const renderSwitch = ({
  label,
  input,
  meta: { touched, invalid, error, submitError },
  style,
  ...others
}) => (
  <FormControl error={touched && invalid}>
    <div style={{
      ...style,
      marginLeft: 5,
    }}
    >
      <Switch
        checked={!!input.value}
        onCheckedChange={checked => input.onChange(checked === true)}
        label={label}
        {...others}
      />
    </div>
    <FormHelperText>{touched && (error || submitError)}</FormHelperText>
  </FormControl>
);

/**
 * @deprecated The component use old form library react-final-form
 */
const OldSwitchField = props => (
  <Field name={props.name} component={renderSwitch} {...props} />
);

export default OldSwitchField;
