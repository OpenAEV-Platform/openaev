import { AnalyticsOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import EntityCard from '../../../../components/common/card/EntityCard';
import { useFormatter } from '../../../../components/i18n';
import { type CustomDashboard } from '../../../../utils/api-types';
import CustomDashboardPopover from './CustomDashboardPopover';

// Height of the Figma card (Design_System_2026, node 8461:13606).
const CARD_HEIGHT = 250;

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
    // DS 2026 layout (Figma Design_System_2026, node 8461:13606): framed icon,
    // type and title, description, last update line, ⋮ menu in the footer band.
    <EntityCard
      testId="custom-dashboard-card"
      layout="ds2026"
      ds2026Height={CARD_HEIGHT}
      to={`/admin/workspaces/custom_dashboards/${customDashboard.custom_dashboard_id}`}
      icon={<AnalyticsOutlined sx={{ fontSize: 24 }} />}
      overline={t('Dashboard')}
      title={customDashboard.custom_dashboard_name}
      description={customDashboard.custom_dashboard_description}
      meta={{
        label: `${t('Updated')}:`,
        value: fldt(customDashboard.custom_dashboard_updated_at),
      }}
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
