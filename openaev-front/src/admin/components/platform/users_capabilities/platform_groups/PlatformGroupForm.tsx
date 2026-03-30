import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import ActionButtons from '../../../../../components/common/ActionButtons';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import type { PlatformGroupInput } from '../../../../../utils/api-types';
import { zodImplement } from '../../../../../utils/Zod';

interface Props {
  onSubmit: SubmitHandler<PlatformGroupInput>;
  onCancel: () => void;
  editing?: boolean;
  initialValues?: PlatformGroupInput;
}

const PlatformGroupForm: FunctionComponent<Props> = ({
  onSubmit,
  onCancel,
  editing,
  initialValues = {
    platform_group_name: '',
    platform_group_description: '',
  },
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const schema = useMemo(
    () =>
      zodImplement<PlatformGroupInput>().with({
        platform_group_name: z.string().min(1, { message: t('Should not be empty') }),
        platform_group_description: z.string().optional(),
      }),
    [t],
  );

  const methods = useForm<PlatformGroupInput>({
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
        id="platformGroupFormId"
        onSubmit={handleSubmit(onSubmit)}
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
      >
        <TextFieldController name="platform_group_name" label={t('Name')} required />
        <TextFieldController name="platform_group_description" label={t('Description')} multiline rows={2} />
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

export default PlatformGroupForm;
