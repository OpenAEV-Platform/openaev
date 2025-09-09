import { Alert, AlertTitle } from '@mui/material';
import { useContext } from 'react';
import { useParams } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import type { CustomDashboard } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CustomDashboardEditHeader from './CustomDashboardEditHeader';
import CustomDashboardWrapper from './CustomDashboardWrapper';
import WidgetCreation from './widgets/WidgetCreation';

const CustomDashboard = () => {
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);
  const { customDashboardId } = useParams() as { customDashboardId: CustomDashboard['custom_dashboard_id'] };

  const configuration = {
    customDashboardId: customDashboardId,
    paramLocalStorageKey: 'custom-dashboard-' + customDashboardId,
  };

  return (
    <CustomDashboardWrapper
      configuration={configuration}
      topSlot={<CustomDashboardEditHeader />}
      bottomSlot={<WidgetCreation />}
      readOnly={!ability.can(ACTIONS.MANAGE, SUBJECTS.DASHBOARDS)}
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
