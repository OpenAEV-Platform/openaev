import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';

import { useFormatter } from '../../../../../components/i18n';
import useAuth from '../../../../../utils/hooks/useAuth';
import XtmHubRegisteredSection from './XtmHubRegisteredSection';
import XtmHubUnregisteredSection from './XtmHubUnregisteredSection';
const XtmHubSettings: React.FC = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { settings } = useAuth();
  const isXTMHubRegistered = settings?.xtm_hub_registration_status === 'registered' || settings?.xtm_hub_registration_status === 'lost_connectivity';

  return (
    <>
      <Typography
        variant="h4"
        gutterBottom
      >
        {t('XTM Hub')}
      </Typography>
      <Paper
        style={{
          padding: theme.spacing(0, 2, 2),
          borderRadius: 4,
          flexGrow: 1,
        }}
        className="paper-for-grid"
        variant="outlined"
      >
        {!isXTMHubRegistered && <XtmHubUnregisteredSection />}

        {isXTMHubRegistered && <XtmHubRegisteredSection />}
      </Paper>
    </>
  );
};

export default XtmHubSettings;
