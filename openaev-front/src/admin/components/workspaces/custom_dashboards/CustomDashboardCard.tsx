import { AnalyticsOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import EntityCard from '../../../../components/common/card/EntityCard';
import { useFormatter } from '../../../../components/i18n';
import { type CustomDashboard } from '../../../../utils/api-types';
import CustomDashboardPopover from './CustomDashboardPopover';

interface Props {
  customDashboard: CustomDashboard;
  onUpdate: (result: CustomDashboard) => void;
  onDelete: (result: string) => void;
}

const CustomDashboardCard: FunctionComponent<Props> = ({
  customDashboard,
  onUpdate,
  onDelete,
}) => {
  const { t, fldt } = useFormatter();

  return (
    <EntityCard
      testId="custom-dashboard-card"
      to={`/admin/workspaces/custom_dashboards/${customDashboard.custom_dashboard_id}`}
      icon={<AnalyticsOutlined sx={{ fontSize: 32 }} />}
      overline={t('Dashboard')}
      title={customDashboard.custom_dashboard_name}
      description={customDashboard.custom_dashboard_description}
      caption={`${t('Updated at')} ${fldt(customDashboard.custom_dashboard_updated_at)}`}
      menu={(
        <CustomDashboardPopover
          customDashboard={customDashboard}
          onUpdate={onUpdate}
          onDelete={onDelete}
          inList
        />
      )}
    />
  );
};

export default CustomDashboardCard;
