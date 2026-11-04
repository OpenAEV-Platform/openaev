import { Box } from '@mui/material';
import { useLocation } from 'react-router';

import MarketplaceTabs from '../../../components/common/marketplace/MarketplaceTabs';
import { useFormatter } from '../../../components/i18n';
import ThreatArsenalAvailable from './catalog/ThreatArsenalAvailable';
import ThreatArsenalInstalled from './ThreatArsenalInstalled';

const ThreatArsenal = () => {
  const { t } = useFormatter();
  const isAvailableTab = useLocation().pathname.endsWith('/available');

  const tabs = (
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
  );

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
