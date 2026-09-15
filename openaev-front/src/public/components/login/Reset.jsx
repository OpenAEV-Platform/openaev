import { Paper } from '@filigran/design-system';
import { Button } from '@mui/material';
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
  const onGoToValidateToken = () => setStep(STEP_VALIDATE_TOKEN);
  return (
    <div className={classes.container}>
      <Paper padding={0}>
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
                    label={t('Email address')}
                    style={{ marginTop: 5 }}
                  />
                  <div style={{
                    marginTop: 30,
                    display: 'flex',
                    gap: 10,
                    justifyContent: 'center',
                  }}
                  >
                    <Button
                      type="submit"
                      variant="contained"
                      color="primary"
                      disabled={pristine || submitting}
                    >
                      {t('Send reset code')}
                    </Button>
                    <Button
                      type="button"
                      variant="outlined"
                      color="primary"
                      onClick={onGoToValidateToken}
                    >
                      {t('I already have a code')}
                    </Button>
                  </div>
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
                    label={t('Enter code')}
                    style={{ marginTop: 5 }}
                  />
                  <Button
                    type="submit"
                    variant="contained"
                    color="primary"
                    disabled={pristine || submitting}
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
                    label={t('Password')}
                    style={{ marginTop: 5 }}
                  />
                  <OldTextField
                    name="password_validation"
                    type="password"
                    label={t('Password validation')}
                    style={{ marginTop: 5 }}
                  />
                  <Button
                    type="submit"
                    variant="contained"
                    color="primary"
                    disabled={pristine || submitting}
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
