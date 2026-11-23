import { Card, CardDescription, CardHeader, CardInfo, CardInfoGroup, CardOverview, CardTitle } from '@filigran/design-system';
import { AnalyticsOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import CardThumbnail from '../../../../components/common/card/CardThumbnail';
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
    // Library Card (Figma Design_System_2026, node 8461:13606): framed icon,
    // type and title, description, last update line. The ⋮ menu is the only
    // action, so it is the header's single action and the card has no footer.
    <Card
      data-testid="custom-dashboard-card"
      to={`/admin/workspaces/custom_dashboards/${customDashboard.custom_dashboard_id}`}
      linkComponent={Link}
    >
      <CardHeader
        icon={<CardThumbnail icon={<AnalyticsOutlined sx={{ fontSize: 24 }} />} />}
        action={(
          <CustomDashboardPopover
            customDashboard={customDashboard}
            onUpdate={onUpdate}
            onDelete={onDelete}
            inList
          />
        )}
      >
        <CardOverview>{t('Dashboard')}</CardOverview>
        <CardTitle>{customDashboard.custom_dashboard_name}</CardTitle>
      </CardHeader>
      {customDashboard.custom_dashboard_description && (
        <CardDescription>{customDashboard.custom_dashboard_description}</CardDescription>
      )}
      <CardInfoGroup>
        <CardInfo label={`${t('Updated')}:`} value={fldt(customDashboard.custom_dashboard_updated_at)} />
      </CardInfoGroup>
    </Card>
  );
};

export default CustomDashboardCard;
