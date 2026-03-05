import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../../components/fields/TextFieldController';
import TagFieldController from '../../../../components/fields/TagFieldController';
import OrganizationFieldController from '../../../../components/fields/OrganizationFieldController';
import type { UserInput } from '../../../../utils/api-types';
import { useFormatter } from '../../../../components/i18n';
import ActionButtons from '../../../../components/common/ActionButtons';

interface Props {
  onSubmit: SubmitHandler<UserInput>;
  onCancel: () => void;
  editing?: boolean;
  hasPassword?: boolean;
  hasPgpKey?: boolean;
  initialValues?: UserInput;
}

const UserForm: FunctionComponent<Props> = ({
  onSubmit,
  onCancel,
  editing,
  hasPassword,
  hasPgpKey,
  initialValues = {
    user_email: '',
    user_firstname: '',
    user_lastname: '',
    user_pgp_key: '',
    user_phone: '',
    user_phone2: '',
    user_organization: '',
    user_tags: [],
    user_plain_password: '',
  },
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const schema = useMemo(
    () =>
      z.object({
        user_email: z.email().min(1, { message: t('Should not be empty') }),
        user_firstname: z.string().optional(),
        user_lastname: z.string().optional(),
        user_pgp_key: z.string().optional(),
        user_phone: z.string().regex(/^\+[\d\s\-.()]+$/, { message: t('Phone number must start with + and contain only digits') }).optional().or(z.literal('')),
        user_phone2: z.string().regex(/^\+[\d\s\-.()]+$/, { message: t('Phone number must start with + and contain only digits') }).optional().or(z.literal('')),
        user_organization: z.string().optional(),
        user_tags: z.string().array().optional(),
        user_plain_password: z.string().optional(),
      }).superRefine((data, ctx) => {
        if (!editing && (!data.user_plain_password || data.user_plain_password.length === 0)) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t('Should not be empty'),
            path: ['user_plain_password'],
          });
        }
      }),
    [t, editing],
  );

  const methods = useForm<UserInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: initialValues,
  });

  const {
    formState: { isDirty, isSubmitting },
    handleSubmit,
  } = methods;

  return (
    <FormProvider {...methods}>
      <form
        id="tenantUserFormId"
        onSubmit={handleSubmit(onSubmit)}
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
      >
        <TextFieldController name="user_email" label={t('Email address')} required />
        <TextFieldController name="user_firstname" label={t('Firstname')} />
        <TextFieldController name="user_lastname" label={t('Lastname')} />
        <OrganizationFieldController name="user_organization" label={t('Organization')} />
        <TextFieldController
          name="user_plain_password"
          label={editing ? t('New password') : t('Password')}
          required={!editing}
          helperText={editing && hasPassword ? t('A password is already set. Fill this field only to replace it.') : undefined}
        />
        <TextFieldController name="user_phone" label={t('Phone number (mobile)')} />
        <TextFieldController name="user_phone2" label={t('Phone number (landline)')} />
        <TextFieldController
          name="user_pgp_key"
          label={editing && hasPgpKey ? t('New PGP public key') : t('PGP public key')}
          multiline rows={5}
          helperText={editing && hasPgpKey ? t('A PGP key is already set. Fill this field only to replace it.') : undefined}
        />
        <TagFieldController name="user_tags" label={t('Tags')} />
        <div style={{ alignSelf: 'flex-end' }}>
          <ActionButtons
            onCancel={onCancel}
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
