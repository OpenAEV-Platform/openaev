import { type CustomDashboard, type PlatformSettings } from '../../../../utils/api-types';
import { type CustomDashboardFormType } from './CustomDashboardForm';

const updateDefaultDashboardsInParameters = (
  customDashboardId: CustomDashboard['custom_dashboard_id'],
  data: CustomDashboardFormType,
  settings: PlatformSettings,
  updatePlatformParameters: (data: PlatformSettings) => void,
) => {
  let defaultDashboardsChanged = false;
  const defaultDashboards = {
    platform_home_dashboard: settings.platform_home_dashboard,
    platform_scenario_dashboard: settings.platform_scenario_dashboard,
    platform_simulation_dashboard: settings.platform_simulation_dashboard,
  };
  ([
    ['is_default_home_dashboard', 'platform_home_dashboard'],
    ['is_default_scenario_dashboard', 'platform_scenario_dashboard'],
    ['is_default_simulation_dashboard', 'platform_simulation_dashboard'],
  ] as [keyof CustomDashboardFormType, keyof typeof defaultDashboards][]).forEach((a) => {
    if (data[a[0]]) {
      defaultDashboards[a[1]] = customDashboardId;
      defaultDashboardsChanged = true;
    }
  });
  if (defaultDashboardsChanged) {
    updatePlatformParameters({
      ...settings,
      ...defaultDashboards,
    });
  }
};

// eslint-disable-next-line import/prefer-default-export
export { updateDefaultDashboardsInParameters };
