import { zodResolver } from '@hookform/resolvers/zod';
import { Box, Button, Tab, Tabs } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FormEvent, type FunctionComponent, type SyntheticEvent, useMemo, useState } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import { updatePlatformParameters } from '../../../../actions/Application';
import type { LoggedHelper } from '../../../../actions/helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import {
  type CustomDashboardInput,
  type PlatformSettings,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { zodImplement } from '../../../../utils/Zod';
import GeneralFormTab from './form/GeneralFormTab';
import ParametersTab from './form/ParametersTab';

type CustomDashboardFormType = CustomDashboardInput & {
  is_default_home_dashboard: boolean;
  is_default_scenario_dashboard: boolean;
  is_default_simulation_dashboard: boolean;
};

interface Props {
  onSubmit: SubmitHandler<CustomDashboardInput>;
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

  const dispatch = useAppDispatch();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const tabs = [{
    key: 'General',
    label: 'General',
  }, {
    key: 'Parameters',
    label: 'Parameters',
  }];
  const [activeTab, setActiveTab] = useState(tabs[0].key);
  const handleActiveTabChange = (_: SyntheticEvent, newValue: string) => {
    setActiveTab(newValue);
  };

  const parametersSchema = z.object({
    custom_dashboards_parameter_id: z.string().optional(),
    custom_dashboards_parameter_name: z.string().min(1, { message: t('Should not be empty') }),
    custom_dashboards_parameter_type: z.enum(['scenario', 'simulation', 'timeRange', 'startDate', 'endDate']),
  });

  const validationSchema = useMemo(
    () =>
      zodImplement<CustomDashboardFormType>().with({
        custom_dashboard_name: z.string().min(1, { message: t('Should not be empty') }),
        custom_dashboard_description: z.string().optional(),
        custom_dashboard_parameters: z.array(parametersSchema).optional(),
        is_default_home_dashboard: z.boolean(),
        is_default_scenario_dashboard: z.boolean(),
        is_default_simulation_dashboard: z.boolean(),
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
    getValues,
    formState: { isSubmitting, isDirty },
  } = methods;

  const handleSubmitWithoutDefault = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    // Get all values at once
    const formValues = getValues();

    const getDefaultDashboardId = (isChecked: boolean, currentDefault = '') => {
      if (isChecked) return customDashboardId;
      return currentDefault === customDashboardId ? '' : currentDefault;
    };

    const platformUpdates = {
      platform_home_dashboard: getDefaultDashboardId(formValues.is_default_home_dashboard, settings.platform_home_dashboard),
      platform_scenario_dashboard: getDefaultDashboardId(formValues.is_default_scenario_dashboard, settings.platform_scenario_dashboard),
      platform_simulation_dashboard: getDefaultDashboardId(formValues.is_default_simulation_dashboard, settings.platform_simulation_dashboard),
    };

    // Check if anything actually changed
    if (JSON.stringify(platformUpdates) !== JSON.stringify({
      platform_home_dashboard: settings.platform_home_dashboard,
      platform_scenario_dashboard: settings.platform_scenario_dashboard,
      platform_simulation_dashboard: settings.platform_simulation_dashboard,
    })) {
      dispatch(updatePlatformParameters({
        ...settings,
        ...platformUpdates,
      }));
    }

    handleSubmit(onSubmit)(e);
    handleClose();
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
        onSubmit={handleSubmitWithoutDefault}
      >
        <Tabs
          value={activeTab}
          onChange={handleActiveTabChange}
          aria-label="tabs for payload form"
          sx={{
            borderBottom: 1,
            borderColor: 'divider',
          }}
        >
          {tabs.map(tab => <Tab key={tab.key} label={tab.label} value={tab.key} />)}
        </Tabs>

        {activeTab === 'General' && (
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

        {activeTab === 'Parameters' && (
          <ParametersTab />
        )}

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
