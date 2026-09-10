import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useEffect } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';

import CustomDashboardAutocompleteFieldController from '../../../components/fields/CustomDashboardAutocompleteFieldController';
import SelectFieldController from '../../../components/fields/SelectFieldController';
import { useFormatter } from '../../../components/i18n';
import { type UpdateProfileInput } from '../../../utils/api-types';
import { zodImplement } from '../../../utils/Zod';
import { langItems, themeItems } from '../utils/OptionItems';

export type UserExperienceFormInput = Pick<
  UpdateProfileInput,
  'user_theme' | 'user_lang' | 'user_home_dashboard'
>;

interface UserExperienceFormProps {
  onSubmit: (data: UserExperienceFormInput) => void;
  initialValues: UserExperienceFormInput;
}

const UserExperienceForm: FunctionComponent<UserExperienceFormProps> = ({
  onSubmit,
  initialValues,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const methods = useForm<UserExperienceFormInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<UserExperienceFormInput>().with({
        user_theme: z.string().min(1, { message: t('Should not be empty') }),
        user_lang: z.string().min(1, { message: t('Should not be empty') }),
        user_home_dashboard: z.string().optional(),
      }),
    ),
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

  // keepDirtyValues so that a refresh triggered by another form of the page does not discard
  // the values currently being edited here.
  useEffect(() => {
    reset(initialValues, { keepDirtyValues: true });
  }, [initialValues, reset]);

  return (
    <FormProvider {...methods}>
      <form
        id="userExperienceForm"
        onSubmit={handleSubmitWithoutPropagation}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2.5),
        }}
      >
        <SelectFieldController name="user_theme" label={t('Theme')} items={themeItems(t)} />
        <SelectFieldController name="user_lang" label={t('Language')} items={langItems(t)} />
        <CustomDashboardAutocompleteFieldController name="user_home_dashboard" label={t('Home dashboard')} disabled={false} withPlatformDefault />
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

export default UserExperienceForm;
