import { Button } from '@filigran/design-system';
import * as PropTypes from 'prop-types';
import { Component } from 'react';
import { Form } from 'react-final-form';

import OldTextField from '../../../components/fields/OldTextField';
import inject18n from '../../../components/i18n';

class PasswordFormComponent extends Component {
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
              name="user_current_password"
              type="password"
              label={t('Current password')}
            />
            <OldTextField
              name="user_plain_password"
              type="password"
              label={t('New password')}
              style={{ marginTop: 20 }}
            />
            <OldTextField
              name="password_confirmation"
              type="password"
              label={t('Confirmation')}
              style={{ marginTop: 20 }}
            />
            <div style={{ marginTop: 20 }}>
              <Button type="submit" disabled={pristine || submitting}>
                {t('Update')}
              </Button>
            </div>
          </form>
        )}
      </Form>
    );
  }
}

PasswordFormComponent.propTypes = {
  error: PropTypes.string,
  pristine: PropTypes.bool,
  submitting: PropTypes.bool,
  onSubmit: PropTypes.func.isRequired,
  handleSubmit: PropTypes.func,
  change: PropTypes.func,
};

const PasswordForm = inject18n(PasswordFormComponent);

export default PasswordForm;
