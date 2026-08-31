import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import RichTextField from '../../../components/fields/RichTextField';
import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import { zodImplement } from '../../../utils/Zod';

interface SendLessonsFormInputs {
  subject: string;
  body: string;
}

interface Props {
  onSubmit: SubmitHandler<SendLessonsFormInputs>;
  handleClose: () => void;
  initialValues?: Partial<SendLessonsFormInputs>;
}

const SendLessonsForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  initialValues,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const methods = useForm<SendLessonsFormInputs>({
    mode: 'onChange',
    resolver: zodResolver(
      zodImplement<SendLessonsFormInputs>().with({
        subject: z.string().min(1, { message: t('This field is required.') }),
        body: z.string().min(1, { message: t('This field is required.') }),
      }),
    ),
    defaultValues: {
      subject: '',
      body: '',
      ...initialValues,
    },
  });

  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = methods;

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  return (
    <FormProvider {...methods}>
      <form id="sendLessonsForm" onSubmit={handleSubmitWithoutPropagation}>
        <TextFieldController
          variant="standard"
          name="subject"
          label={t('Subject')}
          style={{ marginTop: 20 }}
        />
        <RichTextField
          name="body"
          label={t('Message')}
          style={{ marginTop: 20 }}
          control={control}
          disabled={false}
          askAi={false}
          inInject={false}
        />
        <div style={{
          float: 'right',
          marginTop: 20,
        }}
        >
          <Button
            variant="outlined"
            color="primary"
            onClick={handleClose}
            style={{ marginRight: 10 }}
            disabled={isSubmitting || Object.keys(errors).length > 0}
          >
            {t('Cancel')}
          </Button>
          <Button variant="contained" color="primary" type="submit" disabled={isSubmitting}>
            {t('Send')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default SendLessonsForm;
