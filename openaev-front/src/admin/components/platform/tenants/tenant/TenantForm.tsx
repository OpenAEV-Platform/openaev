import { zodResolver } from '@hookform/resolvers/zod';
import { TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import { useFormatter } from '../../../../../components/i18n';
import { type TenantInput } from '../../../../../utils/api-types';
import { zodImplement } from '../../../../../utils/Zod';
import FormActions from './FormActions';

interface Props {
  onSubmit: SubmitHandler<TenantInput>;
  onCancel: () => void;
  editing?: boolean;
  initialValues?: TenantInput;
}

const TenantForm: FunctionComponent<Props> = ({
  onSubmit,
  onCancel,
  editing,
  initialValues = {
    tenant_name: '',
    tenant_description: '',
  },
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  const schema = useMemo(
    () =>
      zodImplement<TenantInput>().with({
        tenant_name: z.string().min(1, { message: t('Should not be empty') }),
        tenant_description: z.string().optional(),
      }),
    [t],
  );

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<TenantInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: initialValues,
  });

  return (
    <form
      id="tenantFormId"
      onSubmit={handleSubmit(onSubmit)}
      style={{
        display: 'flex',
        flexDirection: 'column',
        minHeight: '100%',
        gap: theme.spacing(2),
      }}
    >
      <TextField
        variant="standard"
        fullWidth
        label={t('Name')}
        error={!!errors.tenant_name}
        helperText={errors.tenant_name?.message}
        required
        {...register('tenant_name')}
      />

      <TextField
        variant="standard"
        fullWidth
        multiline
        rows={2}
        label={t('Description')}
        error={!!errors.tenant_description}
        helperText={errors.tenant_description?.message}
        {...register('tenant_description')}
      />
      <div style={{ alignSelf: 'flex-end' }}>
        <FormActions
          onCancel={onCancel}
          cancelLabel={t('Cancel')}
          submitLabel={editing ? t('Update') : t('Create')}
          disabled={!isDirty}
          submitting={isSubmitting}
        />
      </div>
    </form>
  );
};

export default TenantForm;
