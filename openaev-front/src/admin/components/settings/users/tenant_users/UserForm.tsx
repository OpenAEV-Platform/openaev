import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { FormProvider, type Resolver, useForm } from 'react-hook-form';
import { z } from 'zod';

import { type UserInputForm, type UserType } from '../../../../../actions/users/users-helper';
import ActionButtons from '../../../../../components/common/ActionButtons';
import OrganizationFieldController from '../../../../../components/fields/OrganizationFieldController';
import SwitchFieldController from '../../../../../components/fields/SwitchFieldController';
import TagFieldController from '../../../../../components/fields/TagFieldController';
import TenantFieldController from '../../../../../components/fields/TenantFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import { PHONE_REGEX, zodImplement } from '../../../../../utils/Zod';

interface UserFormProps {
  onSubmit: (data: UserInputForm) => void;
  initialValues?: Partial<UserInputForm>;
  editing: boolean;
  handleClose: () => void;
  type: UserType;
}

const UserForm: FunctionComponent<UserFormProps> = ({
  onSubmit,
  initialValues = {
    user_email: '',
    user_plain_password: '',
    user_firstname: '',
    user_lastname: '',
    user_organization: undefined,
    user_tags: [],
    user_tenants: [],
    user_phone: '',
    user_phone2: '',
    user_pgp_key: '',
    user_admin: false,
  },
  editing,
  handleClose,
  type,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const phoneValidation = z
    .string()
    .optional()
    .refine(
      val => !val || PHONE_REGEX.test(val),
      t('Phone number must start with + and contain only digits'),
    );

  const optionSchema = z.object({
    id: z.string(),
    label: z.string(),
  });

  const passwordRequiredMessage = t('This field is required.');
  const schema = zodImplement<UserInputForm>().with({
    user_email: z.email(t('Should be a valid email address')),
    user_plain_password: z.string().optional(),
    user_firstname: z.string().optional(),
    user_lastname: z.string().optional(),
    user_organization: optionSchema.optional(),
    user_tags: z.array(optionSchema).optional(),
    user_tenants: z.array(optionSchema).optional(),
    user_phone: phoneValidation as unknown as z.ZodOptional<z.ZodType<string | undefined>>,
    user_phone2: phoneValidation as unknown as z.ZodOptional<z.ZodType<string | undefined>>,
    user_pgp_key: z.string().optional(),
    user_admin: z.boolean().optional(),
  }).refine(
    data => editing || (data.user_plain_password && data.user_plain_password.length > 0),
    {
      path: ['user_plain_password'],
      message: passwordRequiredMessage,
    },
  );

  const methods = useForm<UserInputForm>({
    mode: 'onTouched',
    resolver: zodResolver(schema) as Resolver<UserInputForm>,
    defaultValues: initialValues,
  });

  const { formState: { isSubmitting, isDirty } } = methods;

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    methods.handleSubmit(onSubmit)(e);
  };

  return (
    <FormProvider {...methods}>
      <form
        id="userForm"
        onSubmit={handleSubmitWithoutPropagation}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
      >
        <TextFieldController
          required
          name="user_email"
          label={t('Email address')}
          disabled={initialValues.user_email === 'admin@openaev.io'}
        />
        {!editing && (
          <TextFieldController
            required
            name="user_plain_password"
            label={t('Password')}
            type="password"
          />
        )}
        <TextFieldController name="user_firstname" label={t('Firstname')} />
        <TextFieldController name="user_lastname" label={t('Lastname')} />
        {type === 'PLATFORM' && <TenantFieldController name="user_tenants" label="Tenants" />}
        <OrganizationFieldController name="user_organization" label={t('Organization')} />
        <TagFieldController name="user_tags" label={t('Tags')} />
        <TextFieldController name="user_phone" label={t('Phone number (mobile)')} />
        <TextFieldController name="user_phone2" label={t('Phone number (landline)')} />
        <TextFieldController name="user_pgp_key" label={t('PGP public key')} multiline rows={5} />
        {type === 'PLATFORM' && <SwitchFieldController name="user_admin" label={t('Administrator')} />}
        <div style={{ alignSelf: 'flex-end' }}>
          <ActionButtons
            onCancel={handleClose}
            cancelLabel={t('Cancel')}
            submitLabel={editing ? t('Update') : t('Create')}
            disabled={!isDirty}
            submitting={isSubmitting}
          />
        </div>
      </form>
    </FormProvider>
  );
};

export default UserForm;
