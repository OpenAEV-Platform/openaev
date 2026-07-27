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
import DefaultHomeDashboard from './default_dashboard/DefaultHomeDashboard';
import GenerateGlobalReportQuickButton from './simulations/simulation/generated_reports/GenerateGlobalReportQuickButton';
import CustomDashboardWrapper from './workspaces/custom_dashboards/CustomDashboardWrapper';
import XtmHubDialogPermissionRequired from './xtm_hub/dialog/permission-required/XtmHubDialogPermissionRequired';

const Home = () => {
  const dispatch = useAppDispatch();
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
      <div style={{
        display: 'flex',
        justifyContent: 'flex-end',
        marginBottom: 8,
      }}
      >
        <GenerateGlobalReportQuickButton />
      </div>
      <CustomDashboardWrapper
        configuration={configuration}
        noDashboardSlot={<DefaultHomeDashboard />}
      />
    </>
  );
};

export default Home;
