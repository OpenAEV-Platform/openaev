import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useEffect } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';

import CustomDashboardAutocompleteFieldController from '../../../components/fields/CustomDashboardAutocompleteFieldController';
import { useFormatter } from '../../../components/i18n';
import { type TenantSettingsUpdateInput } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { zodImplement } from '../../../utils/Zod';

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
