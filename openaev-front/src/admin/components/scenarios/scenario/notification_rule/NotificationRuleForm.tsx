import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import DialogDelete from '../../../../../components/common/DialogDelete';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import type { CreateNotificationRuleInput } from '../../../../../utils/api-types';
import { zodImplement } from '../../../../../utils/Zod';

interface Props {
  onSubmit: SubmitHandler<CreateNotificationRuleInput>;
  initialValues: CreateNotificationRuleInput;
  handleClose?: () => void;
  edition: boolean;
  onDelete: () => void;
}

const NotificationRuleForm: FunctionComponent<Props> = ({
  onSubmit,
  initialValues,
  handleClose,
  edition,
  onDelete,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  // Deletion
  const [deletion, setDeletion] = useState(false);
  const handleOpenDelete = () => {
    setDeletion(true);
  };
  const handleCloseDelete = () => setDeletion(false);

  const methods = useForm<CreateNotificationRuleInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<CreateNotificationRuleInput>().with({
        resource_id: z.string(),
        resource_type: z.string(),
        trigger: z.string(),
        type: z.string(),
        subject: z.string().min(1, { message: t('Should not be empty') }),
      }),
    ),
    defaultValues: initialValues,
  });

  const {
    handleSubmit,
    formState: { isSubmitting },
  } = methods;

  return (
    <>
      <FormProvider {...methods}>
        <form
          id="notificationRuleForm"
          style={{
            display: 'flex',
            flexDirection: 'column',
            minHeight: '100%',
            gap: theme.spacing(2),
          }}
          onSubmit={handleSubmit(onSubmit)}
        >
          <TextFieldController
            variant="standard"
            required
            name="subject"
            label={t('Email subject')}
          />

          <TextFieldController
            variant="standard"
            disabled
            label={t('Trigger on')}
            name="trigger"
          />

          <TextFieldController
            variant="standard"
            disabled
            label={t('Notifier')}
            name="type"
          />

          <div style={{ alignSelf: 'flex-end' }}>
            <Button
              variant="contained"
              onClick={edition ? handleOpenDelete : handleClose}
              style={{ marginRight: theme.spacing(1) }}
              disabled={isSubmitting}
              color={edition ? 'error' : 'primary'}
            >
              {edition ? t('Delete') : t('Cancel')}
            </Button>

            <Button
              variant="contained"
              color="secondary"
              type="submit"
              disabled={isSubmitting}
            >
              {edition ? t('Update') : t('Create')}
            </Button>
          </div>
        </form>
      </FormProvider>
      <DialogDelete
        open={deletion}
        handleClose={handleCloseDelete}
        handleSubmit={onDelete}
        text={t('Do you want to delete the notification rule?')}
      />
    </>

  );
};

export default NotificationRuleForm;
