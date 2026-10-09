import { Box } from '@mui/material';
import { Navigate, useLocation } from 'react-router';

import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import { isFeatureEnabled } from '../../../utils/utils';
import ThreatArsenalAvailable from './catalog/ThreatArsenalAvailable';
import ThreatArsenalTabs from './catalog/ThreatArsenalTabs';
import ThreatArsenalInstalled from './ThreatArsenalInstalled';

const ThreatArsenal = () => {
  const { t } = useFormatter();
  const isMarketplace = isFeatureEnabled('MARKETPLACE');
  const isAvailableTab = useLocation().pathname.endsWith('/available');

  if (isAvailableTab && !isMarketplace) {
    return <Navigate to="/admin/threat-arsenal" replace />;
  }

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
    }}
    >
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Threat Arsenal'),
          current: true,
        }]}
      />

      {isMarketplace && (
        <Box sx={{ marginBottom: 2 }}>
          <ThreatArsenalTabs active={isAvailableTab ? 'available' : 'installed'} />
        </Box>
      )}

      {isAvailableTab ? <ThreatArsenalAvailable /> : <ThreatArsenalInstalled />}
    </Box>
  );
};

export default ThreatArsenal;
