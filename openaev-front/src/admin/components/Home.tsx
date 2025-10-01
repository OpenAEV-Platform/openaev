import { fetchPlatformParameters, updatePlatformParameters } from '../../actions/Application';
import type { LoggedHelper } from '../../actions/helper';
import {
  fetchHomeDashboard, homeDashboardAttackPaths,
  homeDashboardCount,
  homeDashboardEntities,
  homeDashboardSeries,
  homeWidgetToEntitiesRuntime,
} from '../../actions/settings/settings-action';
import { useHelper } from '../../store';
import type { PlatformSettings } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { Can } from '../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import CustomDashboardWrapper from './workspaces/custom_dashboards/CustomDashboardWrapper';
import NoDashboardComponent from './workspaces/custom_dashboards/NoDashboardComponent';
import SelectDashboardButton from './workspaces/custom_dashboards/SelectDashboardButton';

const Home = () => {
  const dispatch = useAppDispatch();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  useDataLoader(() => {
    dispatch(fetchPlatformParameters());
  });

  const handleSelectNewDashboard = (dashboardId: string) => {
    dispatch(updatePlatformParameters({
      ...settings,
      platform_home_dashboard: dashboardId,
    }));
  };

  const configuration = {
    customDashboardId: settings.platform_home_dashboard,
    paramLocalStorageKey: 'custom-dashboard-home',
    fetchCustomDashboard: fetchHomeDashboard,
    fetchCount: homeDashboardCount,
    fetchSeries: homeDashboardSeries,
    fetchEntities: homeDashboardEntities,
    fetchEntitiesRuntime: homeWidgetToEntitiesRuntime,
    fetchAttackPaths: homeDashboardAttackPaths,
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
