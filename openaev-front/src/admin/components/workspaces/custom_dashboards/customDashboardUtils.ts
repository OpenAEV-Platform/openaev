import { type TenantSettingsOutput, type TenantSettingsUpdateInput } from '../../../../utils/api-types';
import type CustomDashboard from './CustomDashboard';
import { type CustomDashboardFormType } from './CustomDashboardForm';

const updateDefaultDashboardsInParameters = (
  customDashboardId: CustomDashboard['custom_dashboard_id'],
  data: CustomDashboardFormType,
  tenantSettings: TenantSettingsOutput,
  updateSettings: (data: TenantSettingsUpdateInput) => void,
) => {
  let defaultDashboardsChanged = false;
  const updatedSettings: Partial<TenantSettingsUpdateInput> = {};

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
    settingsKey: keyof TenantSettingsUpdateInput;
    formKey: keyof CustomDashboardFormType;
  }[];

  dashboardConfigs.forEach(({ settingsKey, formKey }) => {
    const currentDefault = tenantSettings[settingsKey] as string ?? '';
    const newDefault = getDefaultDashboardId(data[formKey] as boolean, currentDefault);

    if (currentDefault !== newDefault) {
      defaultDashboardsChanged = true;
      updatedSettings[settingsKey] = newDefault;
    }
  });

  if (defaultDashboardsChanged) {
    updateSettings({
      platform_name: tenantSettings.platform_name,
      platform_theme: tenantSettings.platform_theme,
      platform_lang: tenantSettings.platform_lang,
      platform_home_dashboard: tenantSettings.platform_home_dashboard,
      platform_scenario_dashboard: tenantSettings.platform_scenario_dashboard,
      platform_simulation_dashboard: tenantSettings.platform_simulation_dashboard,
      ...updatedSettings,
    });
  }
};

export default updateDefaultDashboardsInParameters;
