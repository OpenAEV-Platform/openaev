import { Box } from '@mui/material';
import { Navigate, useLocation } from 'react-router';

import MarketplaceTabs from '../../../components/common/marketplace/MarketplaceTabs';
import { useFormatter } from '../../../components/i18n';
import { isFeatureEnabled } from '../../../utils/utils';
import ThreatArsenalAvailable from './catalog/ThreatArsenalAvailable';
import ThreatArsenalInstalled from './ThreatArsenalInstalled';

const ThreatArsenal = () => {
  const { t } = useFormatter();
  const isMarketplace = isFeatureEnabled('MARKETPLACE');
  const isAvailableTab = useLocation().pathname.endsWith('/available');

  const tabs = isMarketplace
    ? (
        <MarketplaceTabs
          active={isAvailableTab ? 'available' : 'installed'}
          tabs={[
            {
              value: 'installed',
              label: t('Active'),
              to: '/admin/threat-arsenal',
            },
            {
              value: 'available',
              label: t('Available'),
              to: '/admin/threat-arsenal/available',
            },
          ]}
        />
      )
    : undefined;

  if (isAvailableTab && !isMarketplace) {
    return <Navigate to="/admin/threat-arsenal" replace />;
  }

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 3,
    }}
    >

      {isAvailableTab ? <ThreatArsenalAvailable tabs={tabs} /> : <ThreatArsenalInstalled tabs={tabs} />}
    </Box>
  );
};

export default ThreatArsenal;
