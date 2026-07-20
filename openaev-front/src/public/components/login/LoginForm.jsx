import { Button, Stack } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Form } from 'react-final-form';

import OldTextField from '../../../components/fields/OldTextField';
import inject18n from '../../../components/i18n';

// Login form aligned with OpenCTI's LoginForm: login + password fields, then
// a row with the "I forgot my password" text action on the left and the
// primary "Sign in" button on the right.
const LoginFormComponent = (props) => {
  const { t, onSubmit, onResetPassword } = props;
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
    <Form onSubmit={onSubmit} validate={validate}>
      {({ handleSubmit, submitting, pristine }) => (
        <form onSubmit={handleSubmit}>
          <OldTextField
            name="username"
            type="text"
            variant="standard"
            label={t('Login')}
            fullWidth={true}
          />
          <OldTextField
            name="password"
            type="password"
            variant="standard"
            label={t('Password')}
            fullWidth={true}
            style={{ marginTop: 16 }}
          />
          <Stack
            mt={3}
            direction="row"
            alignItems="center"
            justifyContent="space-between"
          >
            <Button
              variant="text"
              color="primary"
              onClick={onResetPassword}
              sx={{
                marginLeft: -1,
                fontWeight: 600,
              }}
            >
              {t('I forgot my password')}
            </Button>
            <Button
              type="submit"
              variant="contained"
              color="primary"
              disabled={pristine || submitting}
              onClick={handleSubmit}
            >
              {t('Sign in')}
            </Button>
          </Stack>
        </form>
      )}
    </Form>
  );
};

LoginFormComponent.propTypes = {
  t: PropTypes.func,
  error: PropTypes.string,
  onSubmit: PropTypes.func.isRequired,
  onResetPassword: PropTypes.func,
  handleSubmit: PropTypes.func,
};

const LoginForm = inject18n(LoginFormComponent);

export default LoginForm;
