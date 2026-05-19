import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import ActionButtons from '../../../../../components/common/ActionButtons';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import { type TenantInput } from '../../../../../utils/api-types';
import { zodImplement } from '../../../../../utils/Zod';

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

  const methods = useForm<TenantInput>({
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
        id="tenantFormId"
        onSubmit={handleSubmit(onSubmit)}
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
      >
        <TextFieldController name="tenant_name" label={t('Name')} required />
        <TextFieldController name="tenant_description" label={t('Description')} />
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

export default TenantForm;
