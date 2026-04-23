import { zodResolver } from '@hookform/resolvers/zod';
import { Box, Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { FormProvider, type SubmitErrorHandler, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import type { LoggedHelper } from '../../../../actions/helper';
import Tabs, { type TabsEntry } from '../../../../components/common/tabs/Tabs';
import useTabs from '../../../../components/common/tabs/useTabs';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type CustomDashboardInput, type PlatformSettings } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';
import GeneralFormTab from './form/GeneralFormTab';
import ParametersTab from './form/ParametersTab';

export type CustomDashboardFormType = CustomDashboardInput & {
  is_default_home_dashboard: boolean;
  is_default_scenario_dashboard: boolean;
  is_default_simulation_dashboard: boolean;
};

interface Props {
  onSubmit: SubmitHandler<CustomDashboardFormType>;
  initialValues?: CustomDashboardInput;
  editing?: boolean;
  customDashboardId?: string;
  handleClose: () => void;
}

const CustomDashboardForm: FunctionComponent<Props> = ({
  onSubmit,
  initialValues = {
    custom_dashboard_name: '',
    custom_dashboard_description: '',
    custom_dashboard_parameters: [],
  },
  editing = false,
  customDashboardId,
  handleClose,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const parametersSchema = z.object({
    custom_dashboards_parameter_id: z.string().optional(),
    custom_dashboards_parameter_name: z.string().min(1, { message: t('Should not be empty') }),
    custom_dashboards_parameter_type: z.enum(['scenario', 'simulation', 'timeRange', 'startDate', 'endDate']),
  });

  const validationSchema = useMemo(
    () =>
      zodImplement<CustomDashboardFormType>().with({
        custom_dashboard_name: z.string().min(1, { message: t('Should not be empty') }).describe('General'),
        custom_dashboard_description: z.string().optional().describe('General'),
        custom_dashboard_parameters: z.array(parametersSchema).optional().describe('Parameters'),
        is_default_home_dashboard: z.boolean().describe('General'),
        is_default_scenario_dashboard: z.boolean().describe('General'),
        is_default_simulation_dashboard: z.boolean().describe('General'),
      }),
    [],
  );

  const methods = useForm<CustomDashboardFormType>({
    mode: 'onTouched',
    resolver: zodResolver(validationSchema),
    defaultValues: {
      ...initialValues,
      is_default_home_dashboard: editing && settings.platform_home_dashboard === customDashboardId,
      is_default_scenario_dashboard: editing && settings.platform_scenario_dashboard === customDashboardId,
      is_default_simulation_dashboard: editing && settings.platform_simulation_dashboard === customDashboardId,
    },
  });

  const {
    handleSubmit,
    formState: { isSubmitting, isDirty },
  } = methods;

  const tabEntries: TabsEntry[] = [{
    key: 'General',
    label: t('General'),
  }, {
    key: 'Parameters',
    label: t('Parameters'),
  }];
  const { currentTab, handleChangeTab } = useTabs(tabEntries[0].key);

  const handleSubmitWithErrors: SubmitErrorHandler<CustomDashboardFormType> = (errors) => {
    const errorKeys = Object.keys(errors) as (keyof CustomDashboardFormType)[];
    tabEntries.forEach((tab) => {
      errorKeys.forEach((name) => {
        if (validationSchema.shape[name].description === tab.key) {
          handleChangeTab(tab.key);
          return;
        }
      });
    });
  };

  return (
    <FormProvider {...methods}>
      <form
        id="customDashboardForm"
        style={{
          display: 'grid',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
        onSubmit={handleSubmit(onSubmit, handleSubmitWithErrors)}
      >
        <Tabs
          entries={tabEntries}
          currentTab={currentTab}
          onChange={idx => handleChangeTab(idx)}
        />
        {currentTab === 'General' && (
          <GeneralFormTab
            initialDefaultDashboardIds={
              {
                home: settings.platform_home_dashboard,
                scenario: settings.platform_scenario_dashboard,
                simulation: settings.platform_simulation_dashboard,
              }
            }
          />
        )}

        {currentTab === 'Parameters' && <ParametersTab />}

        <Box sx={{
          display: 'flex',
          justifyContent: 'flex-end',
          mt: 2,
        }}
        >
          <Button
            variant="contained"
            onClick={handleClose}
            sx={{ mr: 1 }}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="secondary"
            type="submit"
            disabled={!isDirty || isSubmitting}
          >
            {editing ? t('Update') : t('Create')}
          </Button>
        </Box>
      </form>
    </FormProvider>
  );
};

export default CustomDashboardForm;
