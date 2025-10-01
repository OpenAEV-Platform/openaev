import { Checkbox as MuiCheckbox, FormControl, FormControlLabel, FormGroup, FormHelperText } from '@mui/material';
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
    <FormGroup
      row={true}
      style={{
        ...style,
        marginLeft: 5,
      }}
    >
      <FormControlLabel
        control={(
          <MuiCheckbox
            checked={!!input.value}
            onChange={(event) => {
              input.onChange(event.target.checked);
            }}
            {...others}
          />
        )}
        disabled={disabled}
        label={label}
      />
    </FormGroup>
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
