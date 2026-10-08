import { Alert } from '@mui/material';

import { fetchPlatformParameters } from '../../actions/Application';
import type { LoggedHelper } from '../../actions/helper';
import {
  fetchTenantHomeDashboard,
  fetchTenantSettings,
  tenantHomeDashboardAttackPaths,
  tenantHomeDashboardAverage,
  tenantHomeDashboardCount,
  tenantHomeDashboardEntities,
  tenantHomeDashboardSeries,
  tenantHomeWidgetToEntitiesRuntime,
} from '../../actions/settings/tenant-settings-action';
import { useFormatter } from '../../components/i18n';
import { useHelper } from '../../store';
import { type TenantSettingsOutput, type User } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { useAbility } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import DefaultHomeDashboard from './default_dashboard/DefaultHomeDashboard';
import CustomDashboardWrapper from './workspaces/custom_dashboards/CustomDashboardWrapper';
import XtmHubDialogPermissionRequired from './xtm_hub/dialog/permission-required/XtmHubDialogPermissionRequired';

const Home = () => {
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const ability = useAbility();
  const canReadDashboards = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS);
  const { tenantSettings, me }: {
    tenantSettings: TenantSettingsOutput;
    me: User;
  } = useHelper((helper: LoggedHelper) => ({
    tenantSettings: helper.getTenantSettings(),
    me: helper.getMe(),
  }));

  useDataLoader(() => {
    dispatch(fetchPlatformParameters());
    dispatch(fetchTenantSettings());
  });

  // Every home dashboard (default or custom) fetches its widgets through endpoints that need
  // "Access tenant settings": without it, show why instead of a grid of refused widgets.
  if (!canReadDashboards) {
    return (
      <>
        <XtmHubDialogPermissionRequired />
        <Alert severity="info">{t('The home dashboard needs the "Access tenant settings" capability. Use the menu to open your scenarios, simulations, atomic testings and Threat Arsenal.')}</Alert>
      </>
    );
  }

  // Resolution order: built-in platform default, overridden by the tenant
  // setting, overridden by the user profile preference. The backend resolves
  // user preference over the tenant setting for the widget data endpoints.
  const resolvedDashboardId = me?.user_home_dashboard || tenantSettings.platform_home_dashboard;
  if (!resolvedDashboardId) {
    return (
      <>
        <XtmHubDialogPermissionRequired />
        <DefaultHomeDashboard />
      </>
    );
  }

  const configuration = {
    customDashboardId: resolvedDashboardId,
    paramLocalStorageKey: 'custom-dashboard-home',
    resultsSource: { source: 'tenant' as const },
    fetchCustomDashboard: fetchTenantHomeDashboard,
    fetchCount: tenantHomeDashboardCount,
    fetchAverage: tenantHomeDashboardAverage,
    fetchSeries: tenantHomeDashboardSeries,
    fetchEntities: tenantHomeDashboardEntities,
    fetchEntitiesRuntime: tenantHomeWidgetToEntitiesRuntime,
    fetchAttackPaths: tenantHomeDashboardAttackPaths,
  };

  return (
    <>
      <XtmHubDialogPermissionRequired />
      <CustomDashboardWrapper
        configuration={configuration}
        noDashboardSlot={<DefaultHomeDashboard />}
      />
    </>
  );
};

export default Home;
