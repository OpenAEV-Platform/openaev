import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useEffect } from 'react';
import { FormProvider, type Resolver, useForm } from 'react-hook-form';
import { z } from 'zod';

import PhoneFieldController from '../../../components/fields/PhoneFieldController';
import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import { type UpdateUserInfoInput } from '../../../utils/api-types';
import { PHONE_REGEX, zodImplement } from '../../../utils/Zod';
interface ProfileFormProps {
  onSubmit: (data: UpdateUserInfoInput) => void;
  initialValues: UpdateUserInfoInput;
}

const ProfileForm: FunctionComponent<ProfileFormProps> = ({
  onSubmit,
  initialValues,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const phoneValidation = z
    .string()
    .optional()
    .refine(
      val => !val || PHONE_REGEX.test(val),
      t('Phone number must start with + and contain only digits'),
    );

  const methods = useForm<UpdateUserInfoInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<UpdateUserInfoInput>().with({
        user_phone: phoneValidation as unknown as z.ZodOptional<z.ZodType<string | undefined>>,
        user_phone2: phoneValidation as unknown as z.ZodOptional<z.ZodType<string | undefined>>,
        user_pgp_key: z.string().optional(),
      }),
    ) as Resolver<UpdateUserInfoInput>,
    defaultValues: initialValues,
  });

  const {
    handleSubmit,
    formState: {
      isSubmitting,
      isDirty,
    },
    reset,
  } = methods;

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  useEffect(() => {
    reset(initialValues);
  }, [initialValues, reset]);

  return (
    <FormProvider {...methods}>
      <form
        id="profileForm"
        onSubmit={handleSubmitWithoutPropagation}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2.5),
        }}
      >
        <PhoneFieldController name="user_phone" label={t('Phone number (mobile)')} />
        <PhoneFieldController name="user_phone2" label={t('Phone number (landline)')} />
        <TextFieldController name="user_pgp_key" label={t('PGP public key')} multiline rows={5} />
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

export default ProfileForm;
