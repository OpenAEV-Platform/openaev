import { Button } from '@mui/material';
import { Form } from 'react-final-form';

import OldRichTextField from '../../../../../components/fields/OldRichTextField';
import OldTextField from '../../../../../components/fields/OldTextField';
import FileField from '../../../../../components/FileField';
import { useFormatter } from '../../../../../components/i18n';

const CommunicationForm = ({ onSubmit, handleClose, initialValues }) => {
  const { t } = useFormatter();
  // Validation
  const validate = (values) => {
    const errors = {};
    const requiredFields = ['communication_subject', 'communication_content'];
    requiredFields.forEach((field) => {
      if (!values[field]) {
        errors[field] = t('This field is required.');
      }
    });
    return errors;
  };
  // Rendering
  return (
    <Form
      keepDirtyOnReinitialize={true}
      initialValues={initialValues}
      onSubmit={onSubmit}
      validate={validate}
      mutators={{
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ handleSubmit, submitting, pristine }) => (
        <form id="communicationForm" onSubmit={handleSubmit}>
          <OldTextField
            variant="standard"
            name="communication_subject"
            fullWidth={true}
            label={t('Subject')}
            disabled={true}
          />
          <OldRichTextField
            name="communication_content"
            label={t('Content')}
            fullWidth={true}
            style={{
              marginTop: 20,
              height: 250,
            }}
          />
          <FileField
            variant="standard"
            type="file"
            name="communication_file"
            label={t('File')}
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
              disabled={pristine || submitting}
            >
              {t('Send')}
            </Button>
          </div>
        </form>
      )}
    </Form>
  );
};

export default CommunicationForm;
