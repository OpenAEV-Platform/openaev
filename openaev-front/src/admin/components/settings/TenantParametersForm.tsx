import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useEffect } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';

import CustomDashboardAutocompleteFieldController from '../../../components/fields/CustomDashboardAutocompleteFieldController';
import SelectFieldController from '../../../components/fields/SelectFieldController';
import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import { type TenantSettingsUpdateInput } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { zodImplement } from '../../../utils/Zod';
import { langItems, themeItems } from '../utils/OptionItems';

interface TenantParametersFormProps {
  onSubmit: (data: TenantSettingsUpdateInput) => void;
  initialValues: TenantSettingsUpdateInput;
  canNotManage: boolean;
}

const TenantParametersForm: FunctionComponent<TenantParametersFormProps> = ({
  onSubmit,
  initialValues,
  canNotManage,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const methods = useForm<TenantSettingsUpdateInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<TenantSettingsUpdateInput>().with({
        platform_name: z.string().min(1, { message: t('Should not be empty') }),
        platform_theme: z.string().min(1, { message: t('Should not be empty') }),
        platform_lang: z.string().min(1, { message: t('Should not be empty') }),
        platform_home_dashboard: z.string().optional(),
        platform_scenario_dashboard: z.string().optional(),
        platform_simulation_dashboard: z.string().optional(),
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
        id="tenantParametersForm"
        onSubmit={handleSubmitWithoutPropagation}
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2.5),
        }}
      >
        <TextFieldController required name="platform_name" label={t('Platform name')} disabled={canNotManage} />
        <SelectFieldController name="platform_theme" label={t('Default theme')} items={themeItems(t)} disabled={canNotManage} />
        <SelectFieldController name="platform_lang" label={t('Default language')} items={langItems(t)} disabled={canNotManage} />
        <CustomDashboardAutocompleteFieldController name="platform_home_dashboard" label={t('Home dashboard')} disabled={canNotManage} />
        <CustomDashboardAutocompleteFieldController name="platform_scenario_dashboard" label={t('Default scenario dashboard')} disabled={canNotManage} />
        <CustomDashboardAutocompleteFieldController name="platform_simulation_dashboard" label={t('Default simulation dashboard')} disabled={canNotManage} />
        <div>
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
            <Button
              variant="contained"
              color="primary"
              type="submit"
              disabled={!isDirty || isSubmitting}
            >
              {t('Update')}
            </Button>
          </Can>
        </div>
      </form>
    </FormProvider>
  );
};

export default TenantParametersForm;
