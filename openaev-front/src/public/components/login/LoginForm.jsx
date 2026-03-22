import { Stack } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Form } from 'react-final-form';

import Button from '../../../components/common/button/Button';
import OldTextField from '../../../components/fields/OldTextField';
import inject18n from '../../../components/i18n';

const LoginFormComponent = (props) => {
  const { t, onSubmit, onForgotPassword } = props;
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
          <Stack
            mt={3}
            direction="row"
            alignItems="center"
            justifyContent="space-between"
          >
            {onForgotPassword ? (
              <Button
                variant="tertiary"
                onClick={onForgotPassword}
                sx={{ ml: -2 }}
              >
                {t('I forgot my password')}
              </Button>
            ) : (<span />)}
            <Button
              type="submit"
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
  handleSubmit: PropTypes.func,
  onForgotPassword: PropTypes.func,
};

const LoginForm = inject18n(LoginFormComponent);

export default LoginForm;
