import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useEffect, useMemo } from 'react';
import { FormProvider, type Resolver, useForm } from 'react-hook-form';
import { z } from 'zod';

import CountryFieldController from '../../../components/fields/CountryFieldController';
import OrganizationFieldController from '../../../components/fields/OrganizationFieldController';
import PhoneFieldController from '../../../components/fields/PhoneFieldController';
import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import type { UpdateProfileInput, UpdateUserInfoInput, User } from '../../../utils/api-types';
import { PHONE_REGEX, zodImplement } from '../../../utils/Zod';

export type ProfileFormInput = Pick<
  UpdateProfileInput,
  'user_email' | 'user_firstname' | 'user_lastname' | 'user_organization' | 'user_country'
> & UpdateUserInfoInput;

interface ProfileFormProps {
  onSubmit: (data: ProfileFormInput) => void;
  initialValues: User;
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

  const defaultValues: ProfileFormInput = useMemo(() => ({
    user_email: initialValues.user_email,
    user_firstname: initialValues.user_firstname ?? '',
    user_lastname: initialValues.user_lastname ?? '',
    user_organization: initialValues.user_organization ?? '',
    user_country: initialValues.user_country ?? '',
    user_phone: initialValues.user_phone ?? '',
    user_phone2: initialValues.user_phone2 ?? '',
    user_pgp_key: initialValues.user_pgp_key ?? '',
  }), [initialValues]);

  const methods = useForm<ProfileFormInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<ProfileFormInput>().with({
        user_email: z.email(t('Should be a valid email address')),
        user_firstname: z.string().min(1, { message: t('Should not be empty') }),
        user_lastname: z.string().min(1, { message: t('Should not be empty') }),
        user_organization: z.string().optional(),
        user_country: z.string().optional(),
        user_phone: phoneValidation as unknown as z.ZodOptional<z.ZodType<string | undefined>>,
        user_phone2: phoneValidation as unknown as z.ZodOptional<z.ZodType<string | undefined>>,
        user_pgp_key: z.string().optional(),
      }),
    ) as Resolver<ProfileFormInput>,
    defaultValues,
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

  // keepDirtyValues so that a refresh triggered by another form of the page does not discard
  // the values currently being edited here.
  useEffect(() => {
    reset(defaultValues, { keepDirtyValues: true });
  }, [defaultValues, reset]);

  return (
    <FormProvider {...methods}>
      <form
        id="profileForm"
        onSubmit={handleSubmitWithoutPropagation}
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2.5),
        }}
      >
        <TextFieldController required name="user_email" label={t('Email address')} disabled={initialValues.user_is_external} />
        <TextFieldController required name="user_firstname" label={t('Firstname')} />
        <TextFieldController required name="user_lastname" label={t('Lastname')} />
        <OrganizationFieldController name="user_organization" label={t('Organization')} />
        <CountryFieldController name="user_country" label={t('Country')} />
        <PhoneFieldController name="user_phone" label={t('Phone number (mobile)')} countryFieldName="user_country" />
        <PhoneFieldController name="user_phone2" label={t('Phone number (landline)')} countryFieldName="user_country" />
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
