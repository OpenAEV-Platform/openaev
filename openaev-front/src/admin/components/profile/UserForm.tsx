import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useEffect } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';

import CountryFieldController from '../../../components/fields/CountryFieldController';
import OrganizationFieldController from '../../../components/fields/OrganizationFieldController';
import SelectFieldController from '../../../components/fields/SelectFieldController';
import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import type { UpdateProfileInput, User } from '../../../utils/api-types';
import { zodImplement } from '../../../utils/Zod';
import { langItems, themeItems } from '../utils/OptionItems';

interface UserFormProps {
  onSubmit: (data: UpdateProfileInput) => void;
  initialValues: User;
}

const UserForm: FunctionComponent<UserFormProps> = ({
  onSubmit,
  initialValues,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const methods = useForm<UpdateProfileInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<UpdateProfileInput>().with({
        user_email: z.string().email(t('Should be a valid email address')),
        user_firstname: z.string().min(1, { message: t('Should not be empty') }),
        user_lastname: z.string().min(1, { message: t('Should not be empty') }),
        user_organization: z.string().optional(),
        user_country: z.string().optional(),
        user_theme: z.string().min(1, { message: t('Should not be empty') }),
        user_lang: z.string().min(1, { message: t('Should not be empty') }),
      }),
    ),
    defaultValues: initialValues,
  });
  const {
    handleSubmit,
    formState: { isSubmitting, isDirty },
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
        id="userForm"
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
        <SelectFieldController name="user_theme" label={t('Theme')} items={themeItems(t)} />
        <SelectFieldController name="user_lang" label={t('Language')} items={langItems(t)} />
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

export default UserForm;
