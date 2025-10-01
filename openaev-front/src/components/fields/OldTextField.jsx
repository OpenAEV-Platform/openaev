import { TextField as MuiTextField } from '@mui/material';
import { Field } from 'react-final-form';

import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';
import { useFormatter } from '../i18n';

const TextFieldBase = ({
  label,
  input,
  meta: { touched, invalid, error, submitError },
  askAi,
  ...others
}) => {
  const { t } = useFormatter();
  return (
    <MuiTextField
      label={label}
      error={touched && invalid}
      helperText={
        touched && ((error && t(error)) || (submitError && t(submitError)))
      }
      {...input}
      {...others}
      InputProps={{
        endAdornment: askAi && (
          <TextFieldAskAI
            variant="text"
            currentValue={input.value}
            setFieldValue={(val) => {
              input.onChange(val);
            }}
            format="text"
            disabled={others.disabled}
          />
        ),
      }}
    />
  );
};

/**
 * @deprecated The component use old form libnary react-final-form
 */
const OldTextField = props => (
  <Field name={props.name} component={TextFieldBase} {...props} />
);

export default OldTextField;
