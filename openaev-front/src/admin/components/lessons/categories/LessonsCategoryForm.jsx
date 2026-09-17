import { Button } from '@filigran/design-system';
import { Form } from 'react-final-form';

import OldTextField from '../../../../components/fields/OldTextField';
import { useFormatter } from '../../../../components/i18n';

const LessonsCategoryForm = (props) => {
  const { t } = useFormatter();
  const { onSubmit, handleClose, initialValues, editing } = props;
  // Functions
  const validate = (values) => {
    const errors = {};
    const requiredFields = ['lessons_category_name', 'lessons_category_order'];
    requiredFields.forEach((field) => {
      if (!values[field]) {
        errors[field] = t('This field is required.');
      }
    });
    return errors;
  };
  const submitForm = (data) => {
    return onSubmit(data);
  };
  // Rendering
  return (
    <Form
      keepDirtyOnReinitialize
      initialValues={initialValues}
      onSubmit={submitForm}
      validate={validate}
    >
      {({ handleSubmit, submitting, errors }) => (
        <form id="lessonsCategoryForm" onSubmit={handleSubmit}>
          <OldTextField
            name="lessons_category_name"
            label={t('Name')}
          />
          <OldTextField
            name="lessons_category_description"
            label={t('Description')}
            style={{ marginTop: 20 }}
          />
          <OldTextField
            name="lessons_category_order"
            label={t('Order')}
            type="number"
            style={{ marginTop: 20 }}
          />
          <div style={{
            float: 'right',
            marginTop: 20,
          }}
          >
            <Button priority="secondary" onClick={handleClose} disabled={submitting} style={{ marginRight: 10 }}>
              {t('Cancel')}
            </Button>
            <Button type="submit" disabled={submitting || Object.keys(errors).length > 0}>
              {editing ? t('Update') : t('Create')}
            </Button>
          </div>
        </form>
      )}
    </Form>
  );
};

export default LessonsCategoryForm;
