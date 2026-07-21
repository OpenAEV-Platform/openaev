import { Button, Stack } from '@mui/material';
import { Form } from 'react-final-form';

import OldTextField from '../../../components/fields/OldTextField';
import { useFormatter } from '../../../components/i18n';

interface LoginFormValues {
  username: string;
  password: string;
}

interface LoginFormProps {
  onSubmit: (values: LoginFormValues) => void;
  onResetPassword: () => void;
}

// Login form aligned with OpenCTI's LoginForm: login + password fields, then
// a row with the "I forgot my password" text action on the left and the
// primary "Sign in" button on the right.
const LoginForm = ({ onSubmit, onResetPassword }: LoginFormProps) => {
  const { t } = useFormatter();

  const validate = (values: LoginFormValues) => {
    const errors: Partial<Record<keyof LoginFormValues, string>> = {};
    const requiredFields: (keyof LoginFormValues)[] = ['username', 'password'];
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

export default LoginForm;
