import { LockOutlined } from '@mui/icons-material';
import { Alert, Button, CircularProgress, FormControl, InputLabel, MenuItem, Select, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { Form } from 'react-final-form';

import ExerciseField from '../../../../components/ExerciseField';
import OldTextField from '../../../../components/fields/OldTextField';
import ScenarioField from '../../../../components/fields/ScenarioField.tsx';
import FileField from '../../../../components/FileField';
import { useFormatter } from '../../../../components/i18n';
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
    folders = [],
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
          {!editing && (
            <>
              <Typography variant="h3" style={{ marginTop: 10 }}>
                {t('File type')}
              </Typography>
              <ToggleButtonGroup
                exclusive
                size="small"
                value={values.document_kind ?? 'DOCUMENT'}
                onChange={(_, value) => {
                  if (value) form.mutators.setValue('document_kind', value);
                }}
                sx={{ '& .MuiToggleButton-root.Mui-selected': { color: 'primary.main' } }}
              >
                <ToggleButton value="DOCUMENT">{t('Regular document')}</ToggleButton>
                <ToggleButton value="MALWARE_SAMPLE">{t('Malware sample')}</ToggleButton>
              </ToggleButtonGroup>
              {values.document_kind === 'MALWARE_SAMPLE' && (
                <Alert severity="warning" icon={<LockOutlined />} style={{ marginTop: 10 }}>
                  {t('This sample will be stored encrypted as a password-protected archive and decrypted on the fly by the implant at detonation time.')}
                </Alert>
              )}
            </>
          )}
          <FormControl variant="standard" fullWidth style={{ marginTop: 20 }}>
            <InputLabel>{t('Folder')}</InputLabel>
            <Select
              value={values.document_folder ?? ''}
              onChange={event => form.mutators.setValue('document_folder', event.target.value)}
            >
              <MenuItem value="">{t('Root')}</MenuItem>
              {folders.map(folder => (
                <MenuItem key={folder.folder_id} value={folder.folder_id}>
                  {folder.folder_name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <OldTextField
            variant="standard"
            name="document_description"
            fullWidth
            multiline
            rows={2}
            label={t('Description')}
            style={{ marginTop: 20 }}
          />
          <ExerciseField
            name="document_exercises"
            values={values}
            label={t('Simulations')}
            setFieldValue={form.mutators.setValue}
            style={{ marginTop: 20 }}
          />
          <ScenarioField
            multiple={true}
            useForm={true}
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
              variant="outlined"
              color="primary"
              onClick={handleClose}
              style={{ marginRight: 10 }}
              disabled={submitting}
            >
              {t('Cancel')}
            </Button>
            <Button
              variant="contained"
              color="primary"
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
