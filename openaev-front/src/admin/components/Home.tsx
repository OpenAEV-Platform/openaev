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
import { useHelper } from '../../store';
import { type TenantSettingsOutput, type User } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import NoAccess from '../../utils/permissions/NoAccess';
import { useAbility } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import DefaultHomeDashboard from './default_dashboard/DefaultHomeDashboard';
import CustomDashboardWrapper from './workspaces/custom_dashboards/CustomDashboardWrapper';
import XtmHubDialogPermissionRequired from './xtm_hub/dialog/permission-required/XtmHubDialogPermissionRequired';

const Home = () => {
  const dispatch = useAppDispatch();
  const ability = useAbility();
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

  // Resolution order: built-in platform default, overridden by the tenant
  // setting, overridden by the user profile preference. The backend resolves
  // user preference over the tenant setting for the widget data endpoints.
  const resolvedDashboardId = me?.user_home_dashboard || tenantSettings.platform_home_dashboard;
  // Same capability as the endpoints each dashboard calls: the custom home
  // dashboard is served by tenant-settings/home-dashboard/* (READ TENANT_SETTING),
  // the default one by dashboards/adhoc/* (READ DASHBOARD).
  const requiredSubject = resolvedDashboardId ? SUBJECTS.TENANT_SETTINGS : SUBJECTS.DASHBOARDS;
  if (!ability.can(ACTIONS.ACCESS, requiredSubject)) {
    return <NoAccess />;
  }
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
