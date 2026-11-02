import { Box } from '@mui/material';
import { useLocation } from 'react-router';

import ThreatArsenalAvailable from './catalog/ThreatArsenalAvailable';
import ThreatArsenalInstalled from './ThreatArsenalInstalled';
import ThreatArsenalTabs from './ThreatArsenalTabs';

const ThreatArsenal = () => {
  const isAvailableTab = useLocation().pathname.endsWith('/available');

  const tabs = <ThreatArsenalTabs active={isAvailableTab ? 'available' : 'installed'} />;

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
