import { Button, MenuItem } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Component } from 'react';
import { Form } from 'react-final-form';

import OldSelectField from '../../../../components/fields/OldSelectField';
import OldTextField from '../../../../components/fields/OldTextField';
import inject18n from '../../../../components/i18n';

class ChannelForm extends Component {
  validate(values) {
    const { t } = this.props;
    const errors = {};
    const requiredFields = ['channel_type', 'channel_name', 'channel_description'];
    requiredFields.forEach((field) => {
      if (!values[field]) {
        errors[field] = t('This field is required.');
      }
    });
    return errors;
  }

  render() {
    const { t, onSubmit, handleClose, initialValues, editing } = this.props;
    return (
      <Form
        keepDirtyOnReinitialize={true}
        initialValues={initialValues}
        onSubmit={onSubmit}
        validate={this.validate.bind(this)}
        mutators={{
          setValue: ([field, value], state, { changeValue }) => {
            changeValue(state, field, () => value);
          },
        }}
      >
        {({ handleSubmit, submitting, pristine }) => (
          <form id="channelForm" onSubmit={handleSubmit}>
            <OldSelectField
              variant="standard"
              label={t('Type')}
              name="channel_type"
              fullWidth={true}
            >
              <MenuItem key="newspaper" value="newspaper">
                {t('newspaper')}
              </MenuItem>
              <MenuItem key="microblogging" value="microblogging">
                {t('microblogging')}
              </MenuItem>
              <MenuItem key="tv" value="tv">
                {t('tv')}
              </MenuItem>
            </OldSelectField>
            <OldTextField
              variant="standard"
              name="channel_name"
              fullWidth={true}
              label={t('Name')}
              style={{ marginTop: 20 }}
            />
            <OldTextField
              variant="standard"
              name="channel_description"
              fullWidth={true}
              label={t('Subtitle')}
              style={{ marginTop: 20 }}
            />
            <div style={{
              float: 'right',
              marginTop: 20,
            }}
            >
              <Button
                onClick={handleClose.bind(this)}
                style={{ marginRight: 10 }}
                disabled={submitting}
              >
                {t('Cancel')}
              </Button>
              <Button
                color="secondary"
                type="submit"
                disabled={pristine || submitting}
              >
                {editing ? t('Update') : t('Create')}
              </Button>
            </div>
          </form>
        )}
      </Form>
    );
  }
}

ChannelForm.propTypes = {
  t: PropTypes.func,
  onSubmit: PropTypes.func.isRequired,
  handleClose: PropTypes.func,
  editing: PropTypes.bool,
};

export default inject18n(ChannelForm);
