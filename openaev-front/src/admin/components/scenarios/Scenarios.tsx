import { Box } from '@mui/material';
import { Navigate, useLocation } from 'react-router';

import Breadcrumbs from '../../../components/Breadcrumbs';
import MarketplaceTabs from '../../../components/common/marketplace/MarketplaceTabs';
import { useFormatter } from '../../../components/i18n';
import { isFeatureEnabled } from '../../../utils/utils';
import ScenariosAvailable from './catalog/ScenariosAvailable';
import ScenariosActive from './ScenariosActive';

const Scenarios = () => {
  const { t } = useFormatter();
  const isMarketplace = isFeatureEnabled('MARKETPLACE');
  const isAvailableTab = useLocation().pathname.endsWith('/available');

  if (isAvailableTab && !isMarketplace) {
    return <Navigate to="/admin/scenarios" replace />;
  }

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Scenarios'),
          current: true,
        }]}
      />
      {isMarketplace && (
        <Box sx={{ marginBottom: 2 }}>
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
        </Box>
      )}
      {isAvailableTab ? <ScenariosAvailable /> : <ScenariosActive />}
    </>
  );
};

export default Scenarios;
