import { Button, CircularProgress } from '@mui/material';
import { Form } from 'react-final-form';

import ExerciseField from '../../../../components/ExerciseField';
import OldTextField from '../../../../components/fields/OldTextField';
import FileField from '../../../../components/FileField';
import { useFormatter } from '../../../../components/i18n';
import ScenarioField from '../../../../components/ScenarioField';
import TagField from '../../../../components/TagField';

const DocumentForm = (props) => {
  // Standard hooks
  const { t } = useFormatter();
  const {
    initialValues,
    editing,
    onSubmit,
    handleClose,
    filters,
  } = props;

  const validate = (values) => {
    const errors = {};
    let requiredFields = [];
    if (!editing) {
      requiredFields = ['document_file'];
    }
    requiredFields.forEach((field) => {
      const data = values[field];
      if (Array.isArray(data) && data.length === 0) {
        errors[field] = t('This field is required.');
      } else if (!data) {
        errors[field] = t('This field is required.');
      }
    });
    return errors;
  };

  return (
    <Form
      keepDirtyOnReinitialize
      initialValues={initialValues}
      onSubmit={onSubmit}
      validate={validate}
      mutators={{
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ handleSubmit, form, values, submitting, pristine }) => (
        <form id="documentForm" onSubmit={handleSubmit}>
          <OldTextField
            variant="standard"
            name="document_description"
            fullWidth
            multiline
            rows={2}
            label={t('Description')}
            style={{ marginTop: 10 }}
          />
          <ExerciseField
            name="document_exercises"
            values={values}
            label={t('Simulations')}
            setFieldValue={form.mutators.setValue}
            style={{ marginTop: 20 }}
          />
          <ScenarioField
            name="document_scenarios"
            values={values}
            label={t('Scenarios')}
            setFieldValue={form.mutators.setValue}
            style={{ marginTop: 20 }}
          />
          <TagField
            name="document_tags"
            values={values}
            label={t('Tags')}
            setFieldValue={form.mutators.setValue}
            style={{ marginTop: 20 }}
          />
          {!editing && (
            <FileField
              variant="standard"
              type="file"
              name="document_file"
              label={t('File')}
              style={{ marginTop: 20 }}
              filters={filters}
            />
          )}
          <div style={{
            float: 'right',
            marginTop: 20,
          }}
          >
            <Button
              variant="contained"
              onClick={handleClose}
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
              startIcon={submitting && <CircularProgress size={20} />}
            >
              {editing ? t('Update') : t('Create')}
            </Button>
          </div>
        </form>
      )}
    </Form>
  );
};

export default DocumentForm;
