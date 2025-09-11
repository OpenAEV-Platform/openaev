import { Button } from '@mui/material';
import { useNavigate } from 'react-router';

import { fetchPlatformParameters } from '../../actions/Application';
import type { LoggedHelper } from '../../actions/helper';
import {
  fetchHomeDashboard, homeDashboardAttackPaths,
  homeDashboardCount,
  homeDashboardEntities,
  homeDashboardSeries,
} from '../../actions/settings/settings-action';
import { useFormatter } from '../../components/i18n';
import { useHelper } from '../../store';
import type { PlatformSettings } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { Can } from '../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import CustomDashboardWrapper from './workspaces/custom_dashboards/CustomDashboardWrapper';
import NoDashboardComponent from './workspaces/custom_dashboards/NoDashboardComponent';

const Home = () => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  useDataLoader(() => {
    dispatch(fetchPlatformParameters());
  });

  const handleRedirectionToSettings = () => {
    navigate(`/admin/settings/parameters`);
  };

  const configuration = {
    customDashboardId: settings.platform_home_dashboard,
    paramLocalStorageKey: 'custom-dashboard-home',
    fetchCustomDashboard: () => fetchHomeDashboard(),
    fetchCount: (widgetId: string, params: Record<string, string | undefined>) => homeDashboardCount(widgetId, params),
    fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => homeDashboardSeries(widgetId, params),
    fetchEntities: (widgetId: string, params: Record<string, string | undefined>) => homeDashboardEntities(widgetId, params),
    fetchAttackPaths: (widgetId: string, params: Record<string, string | undefined>) => homeDashboardAttackPaths(widgetId, params),
  };

  return (
    <CustomDashboardWrapper
      configuration={configuration}
      noDashboardSlot={(
        <NoDashboardComponent
          actionComponent={(
            <Can I={ACTIONS.ACCESS} a={SUBJECTS.PLATFORM_SETTINGS}>
              <Button onClick={handleRedirectionToSettings} variant="text">{t('Select a dashboard in settings')}</Button>
            </Can>
          )}
        />
      )}
    />
  );
};

export default Home;
