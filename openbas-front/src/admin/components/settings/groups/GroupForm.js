import { InfoOutlined } from '@mui/icons-material';
import { Button, FormControlLabel, GridLegacy, Switch, Tooltip, Typography } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { Field, Form } from 'react-final-form';

import OldSwitchField from '../../../../components/fields/OldSwitchField';
import OldTextField from '../../../../components/fields/OldTextField';
import inject18n from '../../../../components/i18n';
import {
  defaultGrantAtomicTestingObserver, defaultGrantAtomicTestingPlanner,
  defaultGrantPayloadObserver, defaultGrantPayloadPlanner,
  defaultGrantScenarioObserver,
  defaultGrantScenarioPlanner, defaultGrantSimulationObserver,
  defaultGrantSimulationPlanner,
  isDefaultGrantPresent,
} from './GroupUtils.js';

class GroupForm extends Component {
  validate(values) {
    const { t } = this.props;
    const errors = {};
    const requiredFields = ['group_name'];
    requiredFields.forEach((field) => {
      if (!values[field]) {
        errors[field] = t('This field is required.');
      }
    });
    return errors;
  }

  handleDefaultGrantChange(defaultGrant, input, event) {
    const newValue = event.target.checked
      ? [...(input.value || []), defaultGrant]
      : (input.value || []).filter(
          grant => !R.equals(grant, defaultGrant),
        );
    input.onChange(newValue);
  }

  render() {
    const { t, onSubmit, initialValues, handleClose, editing } = this.props;
    return (
      <Form
        keepDirtyOnReinitialize={true}
        initialValues={initialValues}
        onSubmit={onSubmit}
        validate={this.validate.bind(this)}
      >
        {({ handleSubmit, pristine, submitting }) => (
          <form id="groupForm" onSubmit={handleSubmit}>
            <OldTextField
              variant="standard"
              name="group_name"
              fullWidth
              label={t('Name')}
              style={{ marginTop: 10 }}
            />
            <OldTextField
              variant="standard"
              name="group_description"
              fullWidth
              multiline
              rows={3}
              label={t('Description')}
              style={{ marginTop: 20 }}
            />
            <GridLegacy container spacing={3} style={{ marginTop: 0 }}>
              <GridLegacy item xs={12} style={{ display: 'flex' }}>
                <OldSwitchField
                  name="group_default_user_assign"
                  label={t('Auto assign')}
                />
                <Tooltip
                  title={t(
                    'The new users will automatically be assigned to this group.',
                  )}
                >
                  <InfoOutlined
                    fontSize="small"
                    color="primary"
                    style={{ marginTop: 8 }}
                  />
                </Tooltip>
              </GridLegacy>
              <GridLegacy item xs={12}>
                <GridLegacy container spacing={3}>
                  <GridLegacy item xs={12}>
                    <Typography variant="h2" style={{ marginBottom: 0 }}>
                      {t('Scenario')}
                    </Typography>
                  </GridLegacy>
                  <GridLegacy item xs={6} style={{ display: 'flex' }}>
                    <InfoOutlined
                      fontSize="small"
                      color="primary"
                      style={{ marginTop: 8 }}
                    />
                  </GridLegacy>
                </GridLegacy>
              </GridLegacy>
              <GridLegacy item xs={12}>
                <GridLegacy container spacing={3}>
                  <GridLegacy item xs={12}>
                    <Typography variant="h2" style={{ marginBottom: 0 }}>
                      {t('Payloads')}
                    </Typography>
                  </GridLegacy>
                  <GridLegacy item xs={6} style={{ display: 'flex' }}>
                    <Field name="group_default_grants" subscription={{ value: true }}>
                      {({ input }) => {
                        const isChecked = isDefaultGrantPresent(input.value, defaultGrantPayloadObserver);

                        return (
                          <FormControlLabel
                            control={(
                              <Switch
                                checked={isChecked}
                                onChange={event => this.handleDefaultGrantChange(defaultGrantPayloadObserver, input, event)}
                              />
                            )}
                            label={t('Auto observer')}
                          />
                        );
                      }}
                    </Field>
                    <Tooltip
                      title={t(
                        'This group will have observer permission on new payloads.',
                      )}
                    >
                      <InfoOutlined
                        fontSize="small"
                        color="primary"
                        style={{ marginTop: 8 }}
                      />
                    </Tooltip>
                  </GridLegacy>
                  <GridLegacy item xs={6} style={{ display: 'flex' }}>
                    <Field name="group_default_grants" subscription={{ value: true }}>
                      {({ input }) => {
                        const isChecked = isDefaultGrantPresent(input.value, defaultGrantPayloadPlanner);

                        return (
                          <FormControlLabel
                            control={(
                              <Switch
                                checked={isChecked}
                                onChange={event => this.handleDefaultGrantChange(defaultGrantPayloadPlanner, input, event)}
                              />
                            )}
                            label={t('Auto planner')}
                          />
                        );
                      }}
                    </Field>
                    <Tooltip
                      title={t(
                        'This group will have planner permission on new payloads.',
                      )}
                    >
                      <InfoOutlined
                        fontSize="small"
                        color="primary"
                        style={{ marginTop: 8 }}
                      />
                    </Tooltip>
                  </GridLegacy>
                </GridLegacy>
              </GridLegacy>
              <GridLegacy item xs={12}>
                <GridLegacy container spacing={3}>
                  <GridLegacy item xs={12}>
                    <Typography variant="h2" style={{ marginBottom: 0 }}>
                      {t('Atomic testings')}
                    </Typography>
                  </GridLegacy>
                  <GridLegacy item xs={6} style={{ display: 'flex' }}>
                    <Field name="group_default_grants" subscription={{ value: true }}>
                      {({ input }) => {
                        const isChecked = isDefaultGrantPresent(input.value, defaultGrantAtomicTestingObserver);

                        return (
                          <FormControlLabel
                            control={(
                              <Switch
                                checked={isChecked}
                                onChange={event => this.handleDefaultGrantChange(defaultGrantAtomicTestingObserver, input, event)}
                              />
                            )}
                            label={t('Auto observer')}
                          />
                        );
                      }}
                    </Field>
                    <Tooltip
                      title={t(
                        'This group will have observer permission on new atomic testings.',
                      )}
                    >
                      <InfoOutlined
                        fontSize="small"
                        color="primary"
                        style={{ marginTop: 8 }}
                      />
                    </Tooltip>
                  </GridLegacy>
                  <GridLegacy item xs={6} style={{ display: 'flex' }}>
                    <Field name="group_default_grants" subscription={{ value: true }}>
                      {({ input }) => {
                        const isChecked = isDefaultGrantPresent(input.value, defaultGrantAtomicTestingPlanner);

                        return (
                          <FormControlLabel
                            control={(
                              <Switch
                                checked={isChecked}
                                onChange={event => this.handleDefaultGrantChange(defaultGrantAtomicTestingPlanner, input, event)}
                              />
                            )}
                            label={t('Auto planner')}
                          />
                        );
                      }}
                    </Field>
                    <Tooltip
                      title={t(
                        'This group will have planner permission on new atomic testings.',
                      )}
                    >
                      <InfoOutlined
                        fontSize="small"
                        color="primary"
                        style={{ marginTop: 8 }}
                      />
                    </Tooltip>
                  </GridLegacy>
                </GridLegacy>
              </GridLegacy>
            </GridLegacy>
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
                {editing ? t('Update') : t('Create')}
              </Button>
            </div>
          </form>
        )}
      </Form>
    );
  }
}

GroupForm.propTypes = {
  t: PropTypes.func,
  onSubmit: PropTypes.func.isRequired,
  handleClose: PropTypes.func,
  editing: PropTypes.bool,
};

export default inject18n(GroupForm);
