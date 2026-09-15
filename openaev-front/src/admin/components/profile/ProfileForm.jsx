import { Button } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Component } from 'react';
import { Form } from 'react-final-form';

import OldTextField from '../../../components/fields/OldTextField';
import inject18n from '../../../components/i18n';

class ProfileFormComponent extends Component {
  render() {
    const { t, onSubmit, initialValues } = this.props;
    return (
      <Form
        keepDirtyOnReinitialize
        onSubmit={onSubmit}
        initialValues={initialValues}
      >
        {({ handleSubmit, pristine, submitting }) => (
          <form id="profileForm" onSubmit={handleSubmit}>
            <OldTextField
              name="user_phone"
              label={t('Phone number (mobile)')}
            />
            <OldTextField
              name="user_phone2"
              label={t('Phone number (landline)')}
              style={{ marginTop: 20 }}
            />
            <OldTextField
              name="user_pgp_key"
              multiline
              rows={5}
              label={t('PGP public key')}
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

ProfileFormComponent.propTypes = {
  t: PropTypes.func,
  onSubmit: PropTypes.func.isRequired,
};

const ProfileForm = inject18n(ProfileFormComponent);

export default ProfileForm;
