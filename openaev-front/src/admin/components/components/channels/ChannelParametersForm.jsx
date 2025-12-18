import { Button, GridLegacy, MenuItem } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Component } from 'react';
import { Form } from 'react-final-form';

import DeprecatedColorPickerField from '../../../../components/DeprecatedColorPickerField';
import OldSelectField from '../../../../components/fields/OldSelectField';
import OldTextField from '../../../../components/fields/OldTextField';
import inject18n from '../../../../components/i18n';

class ChannelParametersForm extends Component {
  render() {
    const { t, onSubmit, initialValues, disabled } = this.props;
    return (
      <Form
        keepDirtyOnReinitialize={true}
        initialValues={initialValues}
        onSubmit={onSubmit}
        mutators={{
          setValue: ([field, value], state, { changeValue }) => {
            changeValue(state, field, () => value);
          },
        }}
      >
        {({ handleSubmit, submitting, pristine }) => (
          <form id="channelParametersForm" onSubmit={handleSubmit}>
            <GridLegacy container={true} spacing={3}>
              <GridLegacy item={true} xs={6}>
                <OldSelectField
                  variant="standard"
                  label={t('Type')}
                  name="channel_type"
                  fullWidth={true}
                  disabled={disabled}
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
                  disabled={disabled}
                  label={t('Name')}
                  style={{ marginTop: 20 }}
                />
                <DeprecatedColorPickerField
                  variant="standard"
                  name="channel_primary_color_dark"
                  fullWidth={true}
                  disabled={disabled}
                  label={t('Primary color (dark)')}
                  style={{ marginTop: 20 }}
                />
                <DeprecatedColorPickerField
                  variant="standard"
                  name="channel_secondary_color_dark"
                  fullWidth={true}
                  disabled={disabled}
                  label={t('Secondary color (dark)')}
                  style={{ marginTop: 20 }}
                />
              </GridLegacy>
              <GridLegacy item={true} xs={6}>
                <OldTextField
                  variant="standard"
                  name="channel_description"
                  fullWidth={true}
                  disabled={disabled}
                  label={t('Subtitle')}
                />
                <OldSelectField
                  variant="standard"
                  label={t('Header mode')}
                  name="channel_mode"
                  fullWidth={true}
                  disabled={disabled}
                  style={{ marginTop: 20 }}
                >
                  <MenuItem key="title" value="title">
                    {t('title')}
                  </MenuItem>
                  <MenuItem key="logo" value="logo">
                    {t('logo')}
                  </MenuItem>
                  <MenuItem key="logo-title" value="logo-title">
                    {t('logo-title')}
                  </MenuItem>
                </OldSelectField>
                <DeprecatedColorPickerField
                  variant="standard"
                  name="channel_primary_color_light"
                  fullWidth={true}
                  disabled={disabled}
                  label={t('Primary color (light)')}
                  style={{ marginTop: 20 }}
                />
                <DeprecatedColorPickerField
                  variant="standard"
                  name="channel_secondary_color_light"
                  fullWidth={true}
                  disabled={disabled}
                  label={t('Secondary color (light)')}
                  style={{ marginTop: 20 }}
                />
              </GridLegacy>
            </GridLegacy>
            {!disabled && (
              <div style={{ marginTop: 20 }}>
                <Button
                  variant="contained"
                  color="secondary"
                  type="submit"
                  disabled={pristine || submitting}
                >
                  {t('Update')}
                </Button>
              </div>
            )}
          </form>
        )}
      </Form>
    );
  }
}

ChannelParametersForm.propTypes = {
  t: PropTypes.func,
  onSubmit: PropTypes.func.isRequired,
};

export default inject18n(ChannelParametersForm);
