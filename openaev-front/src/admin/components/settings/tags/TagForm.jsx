import { Button } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Component } from 'react';
import { Form } from 'react-final-form';

import DeprecatedColorPickerField from '../../../../components/DeprecatedColorPickerField';
import OldTextField from '../../../../components/fields/OldTextField';
import inject18n from '../../../../components/i18n';

class TagFormComponent extends Component {
  validate(values) {
    const { t } = this.props;
    const errors = {};
    const requiredFields = ['tag_name', 'tag_color'];
    requiredFields.forEach((field) => {
      if (!values[field]) {
        errors[field] = t('This field is required.');
      }
    });
    return errors;
  }

  render() {
    const { t, onSubmit, initialValues, handleClose, editing } = this.props;
    return (
      <Form
        keepDirtyOnReinitialize
        initialValues={initialValues}
        onSubmit={onSubmit}
        validate={this.validate.bind(this)}
      >
        {({ handleSubmit, pristine, submitting }) => (
          <form id="tagForm" onSubmit={handleSubmit}>
            <OldTextField
              name="tag_name"
              fullWidth
              label={t('Value')}
              style={{ marginTop: 10 }}
            />
            <DeprecatedColorPickerField
              name="tag_color"
              fullWidth
              label={t('Color')}
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
                {editing ? t('Update') : t('Create')}
              </Button>
            </div>
          </form>
        )}
      </Form>
    );
  }
}

TagFormComponent.propTypes = {
  t: PropTypes.func,
  onSubmit: PropTypes.func.isRequired,
  handleClose: PropTypes.func,
  editing: PropTypes.bool,
};

const TagForm = inject18n(TagFormComponent);

export default TagForm;
