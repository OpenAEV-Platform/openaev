import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../../components/fields/TextFieldController';
import type { UserInput } from '../../../../utils/api-types';
import { useFormatter } from '../../../../components/i18n';
import ActionButtons from '../../../../components/common/ActionButtons';

interface Props {
  onSubmit: SubmitHandler<UserInput>;
  onCancel: () => void;
}

const UserForm: FunctionComponent<Props> = ({
  onSubmit,
  onCancel,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const schema = useMemo(
    () =>
      z.object({
        user_email: z.email().min(1, { message: t('Should not be empty') }),
        user_pgp_key: z.string().optional(),
        user_plain_password: z.string().min(1, { message: t('Should not be empty') }),
      }),
    [t],
  );

  const methods = useForm<UserInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: {
      user_email: '',
      user_pgp_key: '',
      user_plain_password: '',
    },
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
        <TextFieldController
          name="user_plain_password"
          label={t('Password')}
          required
        />
        <TextFieldController
          name="user_pgp_key"
          label={t('PGP public key')}
          multiline rows={5}
        />
        <div style={{ alignSelf: 'flex-end' }}>
          <ActionButtons
            onCancel={onCancel}
            cancelLabel={t('Cancel')}
            submitLabel={t('Create')}
            disabled={!isDirty}
            submitting={isSubmitting}
          />
        </div>
      </form>
    </FormProvider>
  );
};

export default UserForm;
