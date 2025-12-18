import { Button } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Component } from 'react';
import { Form } from 'react-final-form';

import OldTextField from '../../../components/fields/OldTextField';
import inject18n from '../../../components/i18n';

class ObjectiveForm extends Component {
  validate(values) {
    const { t } = this.props;
    const errors = {};
    const requiredFields = ['objective_title', 'objective_priority'];
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
          <form id="objectiveForm" onSubmit={handleSubmit}>
            <OldTextField
              variant="standard"
              name="objective_title"
              fullWidth
              label={t('Title')}
            />
            <OldTextField
              variant="standard"
              name="objective_description"
              fullWidth
              multiline
              rows={2}
              label={t('Description')}
              style={{ marginTop: 20 }}
            />
            <OldTextField
              variant="standard"
              name="objective_priority"
              fullWidth
              label={t('Priority')}
              style={{ marginTop: 20 }}
              type="number"
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

ObjectiveForm.propTypes = {
  t: PropTypes.func,
  onSubmit: PropTypes.func.isRequired,
  handleClose: PropTypes.func,
  editing: PropTypes.bool,
};

export default inject18n(ObjectiveForm);
