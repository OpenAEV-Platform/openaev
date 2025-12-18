import { Button } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Component } from 'react';
import { Form } from 'react-final-form';

import OldTextField from '../../../components/fields/OldTextField';
import inject18n from '../../../components/i18n';

class PasswordForm extends Component {
  validate(values) {
    const { t } = this.props;
    const errors = {};
    if (
      !values.user_plain_password
      || values.user_plain_password !== values.password_confirmation
    ) {
      errors.user_plain_password = t('Passwords do no match');
    }

    return errors;
  }

  render() {
    const { onSubmit, t } = this.props;
    return (
      <Form onSubmit={onSubmit} validate={this.validate.bind(this)}>
        {({ handleSubmit, pristine, submitting }) => (
          <form id="passwordForm" onSubmit={handleSubmit}>
            <OldTextField
              variant="standard"
              name="user_current_password"
              fullWidth={true}
              type="password"
              label={t('Current password')}
            />
            <OldTextField
              variant="standard"
              name="user_plain_password"
              fullWidth={true}
              type="password"
              label={t('New password')}
              style={{ marginTop: 20 }}
            />
            <OldTextField
              variant="standard"
              name="password_confirmation"
              fullWidth={true}
              type="password"
              label={t('Confirmation')}
              style={{ marginTop: 20 }}
            />
            <div style={{ marginTop: 20 }}>
              <Button
                variant="contained"
                color="primary"
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
  }
}

PasswordForm.propTypes = {
  error: PropTypes.string,
  pristine: PropTypes.bool,
  submitting: PropTypes.bool,
  onSubmit: PropTypes.func.isRequired,
  handleSubmit: PropTypes.func,
  change: PropTypes.func,
};

export default inject18n(PasswordForm);
