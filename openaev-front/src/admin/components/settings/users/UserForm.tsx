import { Button } from '@mui/material';
import { Form } from 'react-final-form';
import { z } from 'zod';

import { type UserInputForm } from '../../../../actions/users/users-helper';
import OldSwitchField from '../../../../components/fields/OldSwitchField';
import OldTextField from '../../../../components/fields/OldTextField';
import { useFormatter } from '../../../../components/i18n';
import OrganizationField from '../../../../components/OrganizationField';
import TagField from '../../../../components/TagField';
import { schemaValidator } from '../../../../utils/Zod';

interface UserFormProps {
  onSubmit: (data: UserInputForm) => void;
  initialValues?: Partial<UserInputForm>;
  editing: boolean;
  handleClose: () => void;
}

const UserForm = ({ onSubmit, initialValues = {}, editing, handleClose }: UserFormProps) => {
  const { t } = useFormatter();

  const requiredFields = editing
    ? ['user_email']
    : ['user_email', 'user_plain_password'];

  const phoneRegex = /^\+\d+$/;

  const userFormSchemaValidation = z.object({
    user_email: z
      .string()
      .nonempty(t('This field is required.'))
      .email(t('Should be a valid email address')),
    ...(requiredFields.includes('user_plain_password') && {
      user_plain_password: z
        .string()
        .nonempty(t('This field is required.')),
    }),
    user_phone: z
      .string()
      .nullable()
      .optional()
      .refine(
        val => !val || phoneRegex.test(val),
        t('Phone number must start with + and contain only digits'),
      ),

    user_phone2: z
      .string()
      .nullable()
      .optional()
      .refine(
        val => !val || phoneRegex.test(val),
        t('Phone number must start with + and contain only digits'),
      ),
  });

  return (
    <Form
      keepDirtyOnReinitialize
      initialValues={initialValues}
      onSubmit={onSubmit}
      validate={schemaValidator(userFormSchemaValidation)}
      mutators={{
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ handleSubmit, form, values, submitting, pristine }) => (
        <form id="userForm" onSubmit={handleSubmit}>
          <OldTextField
            name="user_email"
            fullWidth
            label={t('Email address')}
            disabled={initialValues.user_email === 'admin@openaev.io'}
            style={{ marginTop: 10 }}
          />
          <OldTextField
            name="user_firstname"
            fullWidth
            label={t('Firstname')}
            style={{ marginTop: 20 }}
          />
          <OldTextField
            name="user_lastname"
            fullWidth
            label={t('Lastname')}
            style={{ marginTop: 20 }}
          />
          <OrganizationField
            name="user_organization"
            values={values}
            setFieldValue={form.mutators.setValue}
          />
          {!editing && (
            <OldTextField
              variant="standard"
              name="user_plain_password"
              fullWidth
              type="password"
              label={t('Password')}
              style={{ marginTop: 20 }}
            />
          )}
          {editing && (
            <OldTextField
              variant="standard"
              name="user_phone"
              fullWidth
              label={t('Phone number (mobile)')}
              style={{ marginTop: 20 }}
            />
          )}
          {editing && (
            <OldTextField
              variant="standard"
              name="user_phone2"
              fullWidth
              label={t('Phone number (landline)')}
              style={{ marginTop: 20 }}
            />
          )}
          {editing && (
            <OldTextField
              variant="standard"
              name="user_pgp_key"
              fullWidth
              multiline
              rows={5}
              label={t('PGP public key')}
              style={{ marginTop: 20 }}
            />
          )}
          <TagField
            name="user_tags"
            label={t('Tags')}
            values={values}
            setFieldValue={form.mutators.setValue}
            style={{ marginTop: 20 }}
          />
          <OldSwitchField
            name="user_admin"
            label={t('Administrator')}
            style={{ marginTop: 20 }}
            disabled={initialValues.user_email === 'admin@openaev.io'}
          />
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
            >
              {editing ? t('Update') : t('Create')}
            </Button>
          </div>
        </form>
      )}
    </Form>
  );
};

export default UserForm;
