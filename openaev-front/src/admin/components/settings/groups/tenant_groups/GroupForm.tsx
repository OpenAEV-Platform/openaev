import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import ActionButtons from '../../../../../components/common/ActionButtons';
import SwitchFieldController from '../../../../../components/fields/SwitchFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import { zodImplement } from '../../../../../utils/Zod';

export interface TenantGroupFormInput {
  group_name: string;
  group_description?: string;
  group_default_user_assign?: boolean;
}

interface Props {
  onSubmit: SubmitHandler<TenantGroupFormInput>;
  onCancel: () => void;
  editing?: boolean;
  initialValues?: TenantGroupFormInput;
}

const GroupForm: FunctionComponent<Props> = ({
  onSubmit,
  onCancel,
  editing,
  initialValues = {
    group_name: '',
    group_description: '',
    group_default_user_assign: false,
  },
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const schema = useMemo(
    () =>
      zodImplement<TenantGroupFormInput>().with({
        group_name: z.string().min(1, { message: t('Should not be empty') }),
        group_description: z.string().optional(),
        group_default_user_assign: z.boolean().optional(),
      }),
    [t],
  );

  const methods = useForm<TenantGroupFormInput>({
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
        id="tenantGroupFormId"
        onSubmit={handleSubmit(onSubmit)}
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
      >
        <TextFieldController name="group_name" label={t('Name')} required />
        <TextFieldController name="group_description" label={t('Description')} multiline rows={2} />
        <SwitchFieldController name="group_default_user_assign" label={t('Auto assign')} />
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

export default GroupForm;
