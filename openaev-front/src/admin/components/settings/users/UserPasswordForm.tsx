import { Button } from '@mui/material';
import { Form } from 'react-final-form';

import OldTextField from '../../../../components/fields/OldTextField';
import { useFormatter } from '../../../../components/i18n';

interface UserPasswordFormValues {
  password: string;
  password_validation: string;
}

interface UserPasswordFormProps {
  onSubmit: (values: UserPasswordFormValues) => void;
  initialValues?: Partial<UserPasswordFormValues>;
  handleClose: () => void;
}

const UserPasswordForm = ({
  onSubmit,
  initialValues,
  handleClose,
}: UserPasswordFormProps) => {
  const { t } = useFormatter();

  const validate = (values: UserPasswordFormValues) => {
    const errors: Partial<Record<keyof UserPasswordFormValues, string>> = {};
    if (!values.password || values.password !== values.password_validation) {
      errors.password = t('Passwords do no match');
    }
    return errors;
  };

  return (
    <Form
      initialValues={initialValues}
      onSubmit={onSubmit}
      validate={validate}
    >
      {({ handleSubmit, submitting, pristine }) => (
        <form id="passwordForm" onSubmit={handleSubmit}>
          <OldTextField
            variant="standard"
            name="password"
            fullWidth
            type="password"
            label={t('Password')}
            style={{ marginTop: 10 }}
          />
          <OldTextField
            variant="standard"
            name="password_validation"
            fullWidth
            type="password"
            label={t('Confirmation')}
            style={{ marginTop: 20 }}
          />
          <div
            style={{
              float: 'right',
              marginTop: 20,
            }}
          >
            <Button
              variant="contained"
              onClick={handleClose}
              style={{ marginRight: 10 }}
              disabled={submitting}
            >
              {t('Cancel')}
            </Button>
            <Button
              variant="contained"
              color="secondary"
              type="submit"
              disabled={pristine || submitting}
            >
              {t('Update')}
            </Button>
          </div>
        </form>
      )}
    </Form>
  );
};

export default UserPasswordForm;
