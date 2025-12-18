import { Button } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Form } from 'react-final-form';

import OldTextField from '../../../components/fields/OldTextField';
import inject18n from '../../../components/i18n';

const LoginForm = (props) => {
  const { t, onSubmit } = props;
  const validate = (values) => {
    const errors = {};
    const requiredFields = ['username', 'password'];
    requiredFields.forEach((field) => {
      if (!values[field]) {
        errors[field] = t('This field is required.');
      }
    });
    return errors;
  };
  return (
    <div style={{ padding: 15 }}>
      <Form onSubmit={onSubmit} validate={validate}>
        {({ handleSubmit, submitting, pristine }) => (
          <form onSubmit={handleSubmit}>
            <OldTextField
              name="username"
              type="text"
              variant="standard"
              label={t('Login')}
              fullWidth={true}
              style={{ marginTop: 5 }}
            />
            <OldTextField
              name="password"
              type="password"
              variant="standard"
              label={t('Password')}
              fullWidth={true}
              style={{ marginTop: 20 }}
            />
            <Button
              type="submit"
              variant="contained"
              disabled={pristine || submitting}
              onClick={handleSubmit}
              style={{ marginTop: 30 }}
            >
              {t('Sign in')}
            </Button>
          </form>
        )}
      </Form>
    </div>
  );
};

LoginForm.propTypes = {
  t: PropTypes.func,
  error: PropTypes.string,
  onSubmit: PropTypes.func.isRequired,
  handleSubmit: PropTypes.func,
};

export default inject18n(LoginForm);
