import { Button } from '@filigran/design-system';
import * as PropTypes from 'prop-types';
import { Component } from 'react';
import { Form } from 'react-final-form';

import OldTextField from '../../../components/fields/OldTextField';
import inject18n from '../../../components/i18n';

class ObjectiveFormComponent extends Component {
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
              name="objective_title"
              label={t('Title')}
            />
            <OldTextField
              name="objective_description"
              multiline
              rows={2}
              label={t('Description')}
              style={{ marginTop: 20 }}
            />
            <OldTextField
              name="objective_priority"
              label={t('Priority')}
              style={{ marginTop: 20 }}
              type="number"
            />
            <div style={{
              float: 'right',
              marginTop: 20,
            }}
            >
              <Button priority="secondary" onClick={handleClose.bind(this)} disabled={submitting} style={{ marginRight: 10 }}>
                {t('Cancel')}
              </Button>
              <Button type="submit" disabled={pristine || submitting}>
                {editing ? t('Update') : t('Create')}
              </Button>
            </div>
          </form>
        )}
      </Form>
    );
  }
}

ObjectiveFormComponent.propTypes = {
  t: PropTypes.func,
  onSubmit: PropTypes.func.isRequired,
  handleClose: PropTypes.func,
  editing: PropTypes.bool,
};

const ObjectiveForm = inject18n(ObjectiveFormComponent);

export default ObjectiveForm;
