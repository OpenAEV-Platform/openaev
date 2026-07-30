import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import SelectFieldController from '../../../../components/fields/SelectFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { zodImplement } from '../../../../utils/Zod';

export interface ChannelFormInput {
  channel_type: string;
  channel_name: string;
  channel_description: string;
}

interface Props {
  onSubmit: SubmitHandler<ChannelFormInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: ChannelFormInput;
}

const ChannelForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  initialValues = {
    channel_type: 'newspaper',
    channel_name: '',
    channel_description: '',
  },
  editing = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const methods = useForm<ChannelFormInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<ChannelFormInput>().with({
        channel_type: z.string().min(1, { message: t('Should not be empty') }),
        channel_name: z.string().min(1, { message: t('Should not be empty') }),
        channel_description: z.string().min(1, { message: t('Should not be empty') }),
      }),
    ),
    defaultValues: initialValues,
  });
  const { handleSubmit, formState: { isDirty, isSubmitting } } = methods;

  const typeItems = useMemo(() => [
    {
      value: 'newspaper',
      label: t('newspaper'),
    },
    {
      value: 'microblogging',
      label: t('microblogging'),
    },
    {
      value: 'tv',
      label: t('tv'),
    },
  ], [t]);

  return (
    <FormProvider {...methods}>
      <form id="channelForm" onSubmit={handleSubmit(onSubmit)}>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
        >
          <SelectFieldController
            name="channel_type"
            label={t('Type')}
            items={typeItems}
            required
          />
          <TextFieldController
            variant="standard"
            name="channel_name"
            label={t('Name')}
            required
          />
          <TextFieldController
            variant="standard"
            name="channel_description"
            label={t('Subtitle')}
            required
          />
        </div>
        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: theme.spacing(1),
          marginTop: theme.spacing(2),
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

export default ChannelForm;
