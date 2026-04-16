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
  updateTenantSettings,
} from '../../actions/settings/tenant-settings-action';
import { useHelper } from '../../store';
import { type TenantSettingsOutput } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { Can } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import CustomDashboardWrapper from './workspaces/custom_dashboards/CustomDashboardWrapper';
import NoDashboardComponent from './workspaces/custom_dashboards/NoDashboardComponent';
import SelectDashboardButton from './workspaces/custom_dashboards/SelectDashboardButton';

const Home = () => {
  const dispatch = useAppDispatch();
  const { tenantSettings }: { tenantSettings: TenantSettingsOutput } = useHelper((helper: LoggedHelper) => ({ tenantSettings: helper.getTenantSettings() }));

  useDataLoader(() => {
    dispatch(fetchPlatformParameters());
    dispatch(fetchTenantSettings());
  });

  const handleSelectNewDashboard = async (dashboardId: string) => {
    await updateTenantSettings({ platform_home_dashboard: dashboardId });
    dispatch(fetchTenantSettings());
  };

  const configuration = {
    customDashboardId: tenantSettings.platform_home_dashboard,
    paramLocalStorageKey: 'custom-dashboard-home',
    fetchCustomDashboard: fetchTenantHomeDashboard,
    fetchCount: tenantHomeDashboardCount,
    fetchAverage: tenantHomeDashboardAverage,
    fetchSeries: tenantHomeDashboardSeries,
    fetchEntities: tenantHomeDashboardEntities,
    fetchEntitiesRuntime: tenantHomeWidgetToEntitiesRuntime,
    fetchAttackPaths: tenantHomeDashboardAttackPaths,
  };

  return (
    <CustomDashboardWrapper
      configuration={configuration}
      noDashboardSlot={(
        <NoDashboardComponent
          actionComponent={(
            <Can I={ACTIONS.ACCESS} a={SUBJECTS.PLATFORM_SETTINGS}>
              <SelectDashboardButton
                variant="text"
                handleApplyChange={handleSelectNewDashboard}
              />
            </Can>
          )}
        />
      )}
    />
  );
};

export default Home;
