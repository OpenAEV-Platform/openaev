import { Button, Paper } from '@mui/material';
import { useState } from 'react';
import { Form } from 'react-final-form';
import { useDispatch } from 'react-redux';
import { makeStyles } from 'tss-react/mui';

import { askReset, resetPassword, validateResetToken } from '../../../actions/Application';
import OldTextField from '../../../components/fields/OldTextField';
import { useFormatter } from '../../../components/i18n';

const useStyles = makeStyles()(() => ({
  container: {
    textAlign: 'center',
    margin: '0 auto',
    width: 400,
  },
}));

const validateFields = (t, values, requiredFields) => {
  const errors = {};
  requiredFields.forEach((field) => {
    if (!values[field]) {
      errors[field] = t('This field is required.');
    }
  });
  return errors;
};

const STEP_ASK_RESET = 'ask';
const STEP_VALIDATE_TOKEN = 'validate';
const STEP_RESET_PASSWORD = 'reset';
const Reset = ({ onCancel }) => {
  const { classes } = useStyles();
  const { t, locale } = useFormatter();
  const dispatch = useDispatch();
  const [step, setStep] = useState(STEP_ASK_RESET);
  const [token, setToken] = useState();
  const onSubmitAskToken = (data) => {
    dispatch(askReset(data.username, locale)).then(() => {
      setStep(STEP_VALIDATE_TOKEN);
    });
  };
  const onSubmitValidateToken = (data) => {
    dispatch(validateResetToken(data.code)).then((response) => {
      if (response) {
        setToken(data.code);
        setStep(STEP_RESET_PASSWORD);
      }
    });
  };
  const onSubmitValidatePassword = data => dispatch(resetPassword(token, data));
  return (
    <div className={classes.container}>
      <Paper variant="outlined">
        <div style={{ padding: 15 }}>
          {step === STEP_ASK_RESET && (
            <Form
              onSubmit={onSubmitAskToken}
              validate={values => validateFields(t, values, ['username'])}
            >
              {({ handleSubmit, submitting, pristine }) => (
                <form onSubmit={handleSubmit}>
                  <OldTextField
                    name="username"
                    type="text"
                    variant="standard"
                    label={t('Email address')}
                    fullWidth={true}
                    style={{ marginTop: 5 }}
                  />
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={pristine || submitting}
                    onClick={handleSubmit}
                    style={{ marginTop: 30 }}
                  >
                    {t('Send reset code')}
                  </Button>
                </form>
              )}
            </Form>
          )}
          {step === STEP_VALIDATE_TOKEN && (
            <Form
              onSubmit={onSubmitValidateToken}
              validate={values => validateFields(t, values, ['code'])}
            >
              {({ handleSubmit, submitting, pristine }) => (
                <form onSubmit={handleSubmit}>
                  <OldTextField
                    name="code"
                    type="text"
                    variant="standard"
                    label={t('Enter code')}
                    fullWidth={true}
                    style={{ marginTop: 5 }}
                  />
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={pristine || submitting}
                    onClick={handleSubmit}
                    style={{ marginTop: 30 }}
                  >
                    {t('Continue')}
                  </Button>
                </form>
              )}
            </Form>
          )}
          {step === STEP_RESET_PASSWORD && (
            <Form
              onSubmit={onSubmitValidatePassword}
              validate={values => validateFields(t, values, ['password', 'password_validation'])}
            >
              {({ handleSubmit, submitting, pristine }) => (
                <form onSubmit={handleSubmit}>
                  <OldTextField
                    name="password"
                    type="password"
                    variant="standard"
                    label={t('Password')}
                    fullWidth={true}
                    style={{ marginTop: 5 }}
                  />
                  <OldTextField
                    name="password_validation"
                    type="password"
                    variant="standard"
                    label={t('Password validation')}
                    fullWidth={true}
                    style={{ marginTop: 5 }}
                  />
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={pristine || submitting}
                    onClick={handleSubmit}
                    style={{ marginTop: 30 }}
                  >
                    {t('Change your password')}
                  </Button>
                </form>
              )}
            </Form>
          )}
          <div style={{
            marginTop: 10,
            cursor: 'pointer',
          }}
          >
            <a onClick={() => onCancel()}>{t('Back to login')}</a>
          </div>
        </div>
      </Paper>
    </div>
  );
};

export default Reset;
