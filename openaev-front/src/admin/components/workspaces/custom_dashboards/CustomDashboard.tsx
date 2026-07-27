import { Alert, AlertTitle } from '@mui/material';
import { useContext, useMemo } from 'react';
import { useParams } from 'react-router';

import { fetchCustomDashboard } from '../../../../actions/custom_dashboards/customdashboard-action';
import {
  attackPaths,
  average,
  count,
  entities,
  series,
  widgetToEntitiesRuntime,
} from '../../../../actions/dashboards/dashboard-action';
import { useFormatter } from '../../../../components/i18n';
import type { CustomDashboard, Pagination, WidgetToEntitiesInput } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CustomDashboardEditHeader from './CustomDashboardEditHeader';
import CustomDashboardWrapper from './CustomDashboardWrapper';

const CustomDashboard = () => {
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);
  const { customDashboardId } = useParams() as { customDashboardId: CustomDashboard['custom_dashboard_id'] };

  const configuration = useMemo(() => ({
    customDashboardId: customDashboardId,
    paramLocalStorageKey: 'custom-dashboard-' + customDashboardId,
    resultsSource: { source: 'workspace' as const },
    fetchCustomDashboard: () => fetchCustomDashboard(customDashboardId),
    fetchAverage: (widgetId: string, params: Record<string, string | undefined>) => average(widgetId, params),
    fetchCount: (widgetId: string, params: Record<string, string | undefined>) => count(widgetId, params),
    fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => series(widgetId, params),
    fetchEntities: (widgetId: string, params: Record<string, string | undefined>, pagination?: Pagination) => entities(widgetId, params, pagination),
    fetchEntitiesRuntime: (widgetId: string, input: WidgetToEntitiesInput) => widgetToEntitiesRuntime(widgetId, input),
    fetchAttackPaths: (widgetId: string, params: Record<string, string | undefined>) => attackPaths(widgetId, params),
  }), [customDashboardId]);

  return (
    <CustomDashboardWrapper
      configuration={configuration}
      topSlot={<CustomDashboardEditHeader />}
      readOnly={ability.cannot(ACTIONS.MANAGE, SUBJECTS.DASHBOARDS)}
      noDashboardSlot={(
        <Alert severity="warning">
          <AlertTitle>{t('Warning')}</AlertTitle>
          {t('Custom dashboard is currently unavailable or you do not have sufficient permissions to access it.')}
        </Alert>
      )}
    />
  );
};

export default CustomDashboard;
