import { Field } from 'react-final-form';

import { useFormatter } from '../i18n';
import TextFieldFds from './TextFieldFds';

const TextFieldBase = ({
  label,
  input,
  meta: { touched, invalid, error, submitError },
  ...others
}) => {
  const { t } = useFormatter();
  const message = touched && ((error && t(error)) || (submitError && t(submitError)));
  return (
    <TextFieldFds
      label={label}
      error={touched && invalid ? (message || true) : undefined}
      {...input}
      {...others}
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
