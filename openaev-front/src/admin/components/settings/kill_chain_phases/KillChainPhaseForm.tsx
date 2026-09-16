import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { type KillChainPhaseCreateInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';

interface Props {
  onSubmit: SubmitHandler<KillChainPhaseCreateInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: Partial<KillChainPhaseCreateInput>;
}

const KillChainPhaseForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing = false,
  initialValues = {
    phase_name: '',
    phase_shortname: '',
    phase_kill_chain_name: '',
    phase_external_id: '',
  },
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  // TextFieldController stores its value as a string, even with type="number":
  // coerce it back to a number (undefined when emptied, which fails the
  // required check) before validation.
  const orderNumber = z.preprocess(
    value => (value === '' || value === null ? undefined : value),
    z.coerce.number({ message: t('This field is required.') }),
  ) as unknown as z.ZodOptional<z.ZodNumber>;

  const schema = zodImplement<KillChainPhaseCreateInput>().with({
    phase_name: z.string().min(1, { message: t('This field is required.') }),
    phase_shortname: z.string().min(1, { message: t('This field is required.') }),
    phase_kill_chain_name: z.string().min(1, { message: t('This field is required.') }),
    phase_external_id: z.string().min(1, { message: t('This field is required.') }),
    phase_order: orderNumber,
    phase_description: z.string().optional(),
    phase_stix_id: z.string().optional(),
  });

  const methods = useForm<KillChainPhaseCreateInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: {
      phase_name: '',
      phase_shortname: '',
      phase_kill_chain_name: '',
      phase_external_id: '',
      ...initialValues,
    },
  });

  const {
    handleSubmit,
    formState: { isSubmitting, isDirty },
  } = methods;

  return (
    <FormProvider {...methods}>
      <form
        id="killChainPhaseForm"
        onSubmit={handleSubmit(onSubmit)}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
      >
        <TextFieldController name="phase_name" label={t('Phase name')} />
        <TextFieldController name="phase_shortname" label={t('Phase short name')} />
        <TextFieldController name="phase_kill_chain_name" label={t('Kill chain name')} />
        <TextFieldController name="phase_external_id" label={t('External Id')} />
        <TextFieldController name="phase_order" label={t('Order')} type="number" />
        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: theme.spacing(1),
        }}
        >
          <Button
            variant="outlined"
            color="primary"
            onClick={handleClose}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            type="submit"
            disabled={!isDirty || isSubmitting}
          >
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default KillChainPhaseForm;
