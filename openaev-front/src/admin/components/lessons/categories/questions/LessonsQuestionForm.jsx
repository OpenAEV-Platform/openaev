import { Button } from '@mui/material';
import { Form } from 'react-final-form';

import OldTextField from '../../../../../components/fields/OldTextField';
import { useFormatter } from '../../../../../components/i18n';

const LessonsQuestionForm = (props) => {
  const { t } = useFormatter();
  const { onSubmit, handleClose, initialValues, editing } = props;
  // Functions
  const validate = (values) => {
    const errors = {};
    const requiredFields = [
      'lessons_question_content',
      'lessons_question_order',
    ];
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
        <form id="lessonsQuestionForm" onSubmit={handleSubmit}>
          <OldTextField
            variant="standard"
            name="lessons_question_content"
            fullWidth
            label={t('Content')}
          />
          <OldTextField
            variant="standard"
            name="lessons_question_explanation"
            fullWidth
            label={t('Explanation')}
            style={{ marginTop: 20 }}
          />
          <OldTextField
            variant="standard"
            name="lessons_question_order"
            fullWidth
            label={t('Order')}
            type="number"
            style={{ marginTop: 20 }}
          />
          <div style={{
            float: 'right',
            marginTop: 20,
          }}
          >
            <Button
              onClick={handleClose}
              style={{ marginRight: 10 }}
              disabled={submitting}
            >
              {t('Cancel')}
            </Button>
            <Button
              color="secondary"
              type="submit"
              disabled={submitting || Object.keys(errors).length > 0}
            >
              {editing ? t('Update') : t('Create')}
            </Button>
          </div>
        </form>
      )}
    </Form>
  );
};

export default LessonsQuestionForm;
