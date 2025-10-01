import { Button } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Component } from 'react';
import { Form } from 'react-final-form';

import OldTextField from '../../../../components/fields/OldTextField';
import inject18n from '../../../../components/i18n';

class UserPasswordForm extends Component {
  validate(values) {
    const { t } = this.props;
    const errors = {};
    if (!values.password || values.password !== values.password_validation) {
      errors.password = t('Passwords do no match');
    }
    return errors;
  }

  render() {
    const { t, onSubmit, initialValues, handleClose } = this.props;
    return (
      <Form
        initialValues={initialValues}
        onSubmit={onSubmit}
        validate={this.validate.bind(this)}
      >
        {({ handleSubmit, submitting, pristine }) => (
          <form id="passwordForm" onSubmit={handleSubmit}>
            <OldTextField
              variant="standard"
              name="password"
              fullWidth={true}
              type="password"
              label={t('Password')}
              style={{ marginTop: 10 }}
            />
            <OldTextField
              variant="standard"
              name="password_validation"
              fullWidth={true}
              type="password"
              label={t('Confirmation')}
              style={{ marginTop: 20 }}
            />
            <div style={{
              float: 'right',
              marginTop: 20,
            }}
            >
              <Button
                variant="contained"
                onClick={handleClose.bind(this)}
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
  }
}

UserPasswordForm.propTypes = {
  t: PropTypes.func,
  error: PropTypes.string,
  pristine: PropTypes.bool,
  submitting: PropTypes.bool,
  onSubmit: PropTypes.func.isRequired,
  handleSubmit: PropTypes.func,
  change: PropTypes.func,
};

export default inject18n(UserPasswordForm);
