import { type CustomDashboard, type PlatformSettings } from '../../../../utils/api-types';
import { type CustomDashboardFormType } from './CustomDashboardForm';

const updateDefaultDashboardsInParameters = (
  customDashboardId: CustomDashboard['custom_dashboard_id'],
  data: CustomDashboardFormType,
  settings: PlatformSettings,
  updatePlatformParameters: (data: PlatformSettings) => void,
) => {
  let defaultDashboardsChanged = false;
  let updatedSettings = {} as Partial<PlatformSettings>;

  const getDefaultDashboardId = (isChecked: boolean, currentDefault = '') => {
    if (isChecked) return customDashboardId;
    return currentDefault === customDashboardId ? '' : currentDefault;
  };

  const dashboardConfigs = [
    {
      settingsKey: 'platform_home_dashboard',
      formKey: 'is_default_home_dashboard',
    },
    {
      settingsKey: 'platform_scenario_dashboard',
      formKey: 'is_default_scenario_dashboard',
    },
    {
      settingsKey: 'platform_simulation_dashboard',
      formKey: 'is_default_simulation_dashboard',
    },
  ] as {
    settingsKey: keyof PlatformSettings;
    formKey: keyof CustomDashboardFormType;
  }[];

  dashboardConfigs.forEach(({ settingsKey, formKey }) => {
    const currentDefault = settings[settingsKey] as string;
    const newDefault = getDefaultDashboardId(data[formKey] as boolean, currentDefault);

    if (currentDefault !== newDefault) {
      defaultDashboardsChanged = true;
      updatedSettings = {
        ...updatedSettings,
        [settingsKey]: newDefault,
      };
    }
  });

  if (defaultDashboardsChanged) {
    updatePlatformParameters({
      ...settings,
      ...updatedSettings,
    });
  }
};

export default updateDefaultDashboardsInParameters;
