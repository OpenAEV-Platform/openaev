import { Button } from '@mui/material';
import { type FunctionComponent } from 'react';
import { Form } from 'react-final-form';
import { z } from 'zod';

import OldTextField from '../../../../components/fields/OldTextField';
import { useFormatter } from '../../../../components/i18n';
import TagField from '../../../../components/TagField';
import { type Option } from '../../../../utils/Option';
import { schemaValidator } from '../../../../utils/Zod';

export interface OrganizationInputForm {
  organization_name?: string;
  organization_description?: string;
  organization_tags?: Option[];
}

interface Props {
  initialValues: OrganizationInputForm;
  onSubmit: (data: OrganizationInputForm) => void;
  handleClose: () => void;
  editing?: boolean;
}

// Settings > Security > Organizations form. Deliberately separated from the
// business-side form (teams/organizations/OrganizationForm) so the admin
// experience can diverge (e.g. tenant-level fields) without coupling.
const OrganizationForm: FunctionComponent<Props> = ({
  initialValues,
  onSubmit,
  handleClose,
  editing = false,
}) => {
  const { t } = useFormatter();
  const organizationFormSchemaValidation = z.object({ organization_name: z.string().min(1, t('This field is required.')) });
  return (
    <Form
      keepDirtyOnReinitialize
      initialValues={initialValues}
      onSubmit={onSubmit}
      validate={schemaValidator(organizationFormSchemaValidation)}
      mutators={{
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ handleSubmit, form, values, submitting, pristine }) => (
        <form id="organizationForm" onSubmit={handleSubmit}>
          <OldTextField
            variant="standard"
            name="organization_name"
            fullWidth
            label={t('Name')}
          />
          <OldTextField
            variant="standard"
            name="organization_description"
            fullWidth
            multiline
            rows={2}
            label={t('Description')}
            style={{ marginTop: 20 }}
          />
          <TagField
            name="organization_tags"
            label={t('Tags')}
            values={values}
            setFieldValue={form.mutators.setValue}
            style={{ marginTop: 20 }}
          />
          <div style={{
            display: 'flex',
            justifyContent: 'flex-end',
            gap: 10,
            marginTop: 20,
          }}
          >
            <Button
              variant="contained"
              onClick={handleClose}
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
};

export default OrganizationForm;
