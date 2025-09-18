import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';
import { useEffect, useRef } from 'react';

import type { LoggedHelper } from '../../../../../actions/helper';
import { refreshConnectivity } from '../../../../../actions/xtmhub/xtmhub-actions';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import type { PlatformSettings } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useAuth from '../../../../../utils/hooks/useAuth';
import { Can } from '../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import XtmHubRegisteredSection from './XtmHubRegisteredSection';
import XtmHubTab from './XtmHubTab';
import XtmHubUnregisteredSection from './XtmHubUnregisteredSection';
const XtmHubSettings: React.FC = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { isXTMHubAccessible } = useAuth();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const dispatch = useAppDispatch();

  const hasRun = useRef(false);
  useEffect(() => {
    if (!settings.xtm_hub_token || hasRun.current) {
      return;
    }

    hasRun.current = true;
    dispatch(refreshConnectivity());
  }, []);

  const isXTMHubRegistered = settings?.xtm_hub_registration_status === 'registered' || settings?.xtm_hub_registration_status === 'lost_connectivity';

  return (
    <>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        marginBottom: theme.spacing(0.5),
      }}
      >
        <Typography
          variant="h4"
          gutterBottom
          style={{
            display: 'flex',
            alignItems: 'flex-end',
            marginBottom: 0,
            minHeight: theme.spacing(4.5),
          }}
        >
          {t('XTM Hub')}
        </Typography>
        {
          isXTMHubAccessible && settings.xtm_hub_reachable && (
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
              <XtmHubTab />
            </Can>
          )
        }
      </div>
      <Paper
        style={{
          padding: theme.spacing(2),
          borderRadius: 4,
          flexGrow: 1,
        }}
        className="paper-for-grid"
        variant="outlined"
      >
        <Typography variant="h6" style={{ marginBottom: theme.spacing(2) }}>
          {t('Experiment valuable threat management resources in the XTM Hub')}
        </Typography>
        <Typography style={{ marginBottom: theme.spacing(2) }}>
          {t('XTM Hub is a central forum to access resources, share tradecraft, and optimize the use of Filigran\'s products, fostering collaboration and empowering the community.')}
        </Typography>

        {!isXTMHubRegistered && <XtmHubUnregisteredSection />}

        {isXTMHubRegistered && <XtmHubRegisteredSection />}
      </Paper>
    </>
  );
};

export default XtmHubSettings;
