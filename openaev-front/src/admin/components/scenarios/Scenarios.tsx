import { Box } from '@mui/material';
import { useLocation } from 'react-router';

import MarketplaceTabs from '../../../components/common/marketplace/MarketplaceTabs';
import { useFormatter } from '../../../components/i18n';
import ScenariosAvailable from './catalog/ScenariosAvailable';
import ScenariosActive from './ScenariosActive';

const Scenarios = () => {
  const { t } = useFormatter();
  const isAvailableTab = useLocation().pathname.endsWith('/available');

  const tabs = (
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
  );

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
