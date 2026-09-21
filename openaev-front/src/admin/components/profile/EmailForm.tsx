import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useEffect } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import type { UpdateMeEmailInput, User } from '../../../utils/api-types';
import { zodImplement } from '../../../utils/Zod';

interface UserFormProps {
  onSubmit: (data: UpdateMeEmailInput) => void;
  initialValues: User;
}

const EmailForm: FunctionComponent<UserFormProps> = ({
  onSubmit,
  initialValues,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const methods = useForm<UpdateMeEmailInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<UpdateMeEmailInput>().with({
        user_email: z.email(t('Should be a valid email address')).min(1, { message: t('Should not be empty') }),
        user_current_password: z.string().min(1, { message: t('Should not be empty') }),
      }),
    ),
    defaultValues: initialValues,
  });
  const {
    handleSubmit,
    formState: { isValid, isSubmitting },
    reset,
  } = methods;
  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
    reset(initialValues);
  };
  useEffect(() => {
    reset(initialValues);
  }, [initialValues, reset]);

  return (
    <FormProvider {...methods}>
      <form
        id="emailForm"
        onSubmit={handleSubmitWithoutPropagation}
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2.5),
        }}
      >
        <TextFieldController required type="password" name="user_current_password" label={t('Current password')} />
        <TextFieldController required name="user_email" label={t('Email')} />
        <div>
          <Button
            variant="contained"
            color="primary"
            type="submit"
            disabled={!isValid || isSubmitting}
          >
            {t('Update')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default EmailForm;
