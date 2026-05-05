import { zodResolver } from '@hookform/resolvers/zod';
import { Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { cloneElement, type FunctionComponent, type ReactElement, type SyntheticEvent } from 'react';
import { FormProvider, type Resolver, useForm } from 'react-hook-form';
import { z } from 'zod';

import { type UserType } from '../../../../../actions/users/users-helper';
import ActionButtons from '../../../../../components/common/ActionButtons';
import OrganizationFieldController from '../../../../../components/fields/OrganizationFieldController';
import SwitchFieldController from '../../../../../components/fields/SwitchFieldController';
import TagFieldController from '../../../../../components/fields/TagFieldController';
import TenantFieldController from '../../../../../components/fields/TenantFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import { type UserInput } from '../../../../../utils/api-types';
import useEnterpriseEdition from '../../../../../utils/hooks/useEnterpriseEdition';
import { PHONE_REGEX, zodImplement } from '../../../../../utils/Zod';

const ScopedField: FunctionComponent<{
  readOnly: boolean;
  tooltip: string;
  children: ReactElement<{ disabled?: boolean }>;
}> = ({ readOnly, tooltip, children }) => {
  if (!readOnly) return children;
  const disabledChild = cloneElement(children, { disabled: true });
  return (
    <Tooltip title={tooltip} placement="top">
      <div>{disabledChild}</div>
    </Tooltip>
  );
};

interface UserFormProps {
  onSubmit: (data: UserInput) => void;
  initialValues?: Partial<UserInput>;
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
    user_organization: '',
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
  const { isValidated: isEE } = useEnterpriseEdition();

  const phoneValidation = z
    .string()
    .optional()
    .refine(
      val => !val || PHONE_REGEX.test(val),
      t('Phone number must start with + and contain only digits'),
    );

  const passwordRequiredMessage = t('This field is required.');
  const schema = zodImplement<UserInput>().with({
    user_email: z.email(t('Should be a valid email address')),
    user_plain_password: z.string().optional(),
    user_firstname: z.string().optional(),
    user_lastname: z.string().optional(),
    user_organization: z.string().optional(),
    user_tags: z.array(z.string()).optional(),
    user_tenants: z.array(z.string()).optional(),
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

  const methods = useForm<UserInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema) as Resolver<UserInput>,
    defaultValues: initialValues,
  });

  const { formState: { isSubmitting, isDirty } } = methods;

  const isTenantReadOnly = isEE && type === 'TENANT' && editing;
  const platformOnlyTooltip = t('This field can only be edited from the platform settings');

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
        <ScopedField readOnly={isTenantReadOnly} tooltip={platformOnlyTooltip}>
          <TextFieldController
            required
            name="user_email"
            label={t('Email address')}
            disabled={initialValues.user_email === 'admin@openaev.io'}
          />
        </ScopedField>
        {!editing && (
          <TextFieldController
            required
            name="user_plain_password"
            label={t('Password')}
            type="password"
          />
        )}
        <ScopedField readOnly={isTenantReadOnly} tooltip={platformOnlyTooltip}>
          <TextFieldController name="user_firstname" label={t('Firstname')} />
        </ScopedField>
        <ScopedField readOnly={isTenantReadOnly} tooltip={platformOnlyTooltip}>
          <TextFieldController name="user_lastname" label={t('Lastname')} />
        </ScopedField>
        {type === 'PLATFORM' && <TenantFieldController name="user_tenants" label="Tenants" />}
        {type !== 'PLATFORM' && <OrganizationFieldController name="user_organization" label={t('Organization')} />}
        {type !== 'PLATFORM' && <TagFieldController name="user_tags" label={t('Tags')} />}
        <ScopedField readOnly={isTenantReadOnly} tooltip={platformOnlyTooltip}>
          <TextFieldController name="user_phone" label={t('Phone number (mobile)')} />
        </ScopedField>
        <ScopedField readOnly={isTenantReadOnly} tooltip={platformOnlyTooltip}>
          <TextFieldController name="user_phone2" label={t('Phone number (landline)')} />
        </ScopedField>
        <ScopedField readOnly={isTenantReadOnly} tooltip={platformOnlyTooltip}>
          <TextFieldController name="user_pgp_key" label={t('PGP public key')} multiline rows={5} />
        </ScopedField>
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
