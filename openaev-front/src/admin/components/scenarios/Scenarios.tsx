import { Box } from '@mui/material';
import { Navigate, useLocation } from 'react-router';

import MarketplaceTabs from '../../../components/common/marketplace/MarketplaceTabs';
import { useFormatter } from '../../../components/i18n';
import { isFeatureEnabled } from '../../../utils/utils';
import ScenariosAvailable from './catalog/ScenariosAvailable';
import ScenariosActive from './ScenariosActive';

const Scenarios = () => {
  const { t } = useFormatter();
  const isMarketplace = isFeatureEnabled('MARKETPLACE');
  const isAvailableTab = useLocation().pathname.endsWith('/available');

  const tabs = isMarketplace
    ? (
        <MarketplaceTabs
          active={isAvailableTab ? 'available' : 'active'}
          tabs={[
            {
              value: 'active',
              label: t('Active'),
              to: '/admin/scenarios',
            },
            {
              value: 'available',
              label: t('Available'),
              to: '/admin/scenarios/available',
            },
          ]}
        />
      )
    : undefined;

  if (isAvailableTab && !isMarketplace) {
    return <Navigate to="/admin/scenarios" replace />;
  }

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 3,
    }}
    >
      {isAvailableTab ? <ScenariosAvailable tabs={tabs} /> : <ScenariosActive tabs={tabs} />}
    </Box>
  );
};

export default Scenarios;
