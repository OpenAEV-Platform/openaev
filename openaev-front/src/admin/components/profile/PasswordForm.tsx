import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { FormProvider, type Resolver, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';

export interface PasswordFormInput {
  user_current_password: string;
  user_plain_password: string;
  password_confirmation: string;
}

interface PasswordFormProps { onSubmit: (data: PasswordFormInput) => void }

const PasswordForm: FunctionComponent<PasswordFormProps> = ({ onSubmit }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const schema = z
    .object({
      user_current_password: z.string().min(1, { message: t('Should not be empty') }),
      user_plain_password: z.string().min(1, { message: t('Should not be empty') }),
      password_confirmation: z.string().min(1, { message: t('Should not be empty') }),
    })
    .refine(data => data.user_plain_password === data.password_confirmation, {
      path: ['password_confirmation'],
      message: t('Passwords do no match'),
    });

  const methods = useForm<PasswordFormInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema) as Resolver<PasswordFormInput>,
    defaultValues: {
      user_current_password: '',
      user_plain_password: '',
      password_confirmation: '',
    },
  });

  const {
    handleSubmit,
    formState: {
      isSubmitting,
      isDirty,
    },
  } = methods;

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  return (
    <FormProvider {...methods}>
      <form
        id="passwordForm"
        onSubmit={handleSubmitWithoutPropagation}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2.5),
        }}
      >
        <TextFieldController required name="user_current_password" type="password" label={t('Current password')} />
        <TextFieldController required name="user_plain_password" type="password" label={t('New password')} />
        <TextFieldController required name="password_confirmation" type="password" label={t('Confirmation')} />
        <div>
          <Button
            variant="contained"
            color="primary"
            type="submit"
            disabled={!isDirty || isSubmitting}
          >
            {t('Update')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default PasswordForm;
