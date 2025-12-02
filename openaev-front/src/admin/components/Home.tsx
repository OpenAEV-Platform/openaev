import { fetchPlatformParameters, updatePlatformParameters } from '../../actions/Application';
import { getPlatformSettingsSelector } from '../../actions/selectors';
import {
  fetchHomeDashboard, homeDashboardAttackPaths,
  homeDashboardCount,
  homeDashboardEntities,
  homeDashboardSeries,
  homeWidgetToEntitiesRuntime,
} from '../../actions/settings/settings-action';
import { useSelectorHelper } from '../../store';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { Can } from '../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import CustomDashboardWrapper from './workspaces/custom_dashboards/CustomDashboardWrapper';
import NoDashboardComponent from './workspaces/custom_dashboards/NoDashboardComponent';
import SelectDashboardButton from './workspaces/custom_dashboards/SelectDashboardButton';

const Home = () => {
  const dispatch = useAppDispatch();
  const settings = useSelectorHelper(getPlatformSettingsSelector);

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
    customDashboardId: settings?.platform_home_dashboard,
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
