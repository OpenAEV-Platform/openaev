import { Button, Chip, Divider, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';

import type { LoggedHelper } from '../../../../../actions/helper';
import colorStyles from '../../../../../components/Color';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { XTM_HUB_DEFAULT_URL } from '../../../../../utils/Environment';
import useAuth from '../../../../../utils/hooks/useAuth';
import { getChipStyle, getXtmHubLogo } from './XtmHubUtils';

interface XtmHubRegisteredSectionProps { onDisconnect?: () => void }

const XtmHubRegisteredSection: React.FC<XtmHubRegisteredSectionProps> = ({ onDisconnect }) => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const { settings } = useAuth();
  const registration = useHelper((helper: LoggedHelper) => helper.getXtmHubRegistration());
  const xtmHubLogo = getXtmHubLogo(theme);
  const hubUrl = settings?.xtm_hub_url ?? XTM_HUB_DEFAULT_URL;

  const chipStyle = getChipStyle(theme);

  const isConnected = registration?.tenant_xtmhub_registration_status === 'REGISTERED';
  const statusLabel = isConnected ? t('Connected') : t('Connectivity lost');
  const statusBg = isConnected ? colorStyles.green.backgroundColor : colorStyles.red.backgroundColor;
  const connectionDate = registration?.tenant_xtmhub_registration_date
    ? fldt(registration.tenant_xtmhub_registration_date)
    : '-';
  const connectedBy = registration?.tenant_xtmhub_registration_user_name ?? '-';

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: theme.spacing(2),
    }}
    >
      <img src={xtmHubLogo} alt="XTM Hub" style={{ height: 35 }} />

      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(4),
        width: '100%',
      }}
      >
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
        >
          <Typography sx={{
            fontFamily: '"Geologica", sans-serif',
            fontWeight: theme.typography.fontWeightBold,
            fontSize: '1rem',
          }}
          >
            {t('Experiment valuable threat management resources in the XTM Hub')}
          </Typography>

          <div>
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: `${theme.spacing(1.5)} 0`,
            }}
            >
              <Typography variant="body2" color="text.secondary">{t('Connection status')}</Typography>
              <Chip
                style={{
                  ...chipStyle,
                  backgroundColor: statusBg,
                }}
                label={statusLabel}
              />
            </div>
            <Divider />
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: `${theme.spacing(1.5)} 0`,
            }}
            >
              <Typography variant="body2" color="text.secondary">{t('Connection date')}</Typography>
              <Chip
                style={{
                  ...chipStyle,
                  backgroundColor: colorStyles.blue.backgroundColor,
                }}
                label={connectionDate}
              />
            </div>
            <Divider />
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: `${theme.spacing(1.5)} 0`,
            }}
            >
              <Typography variant="body2" color="text.secondary">{t('Connected by')}</Typography>
              <Typography variant="body2" color="text.primary">{connectedBy}</Typography>
            </div>
            <Divider />
          </div>
        </div>

        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: theme.spacing(2),
        }}
        >
          <Button
            variant="outlined"
            component="a"
            href={hubUrl}
            target="_blank"
            rel="noreferrer"
            sx={{
              'textTransform': 'none',
              'fontWeight': 600,
              'borderColor': theme.palette.border.primary,
              '&:hover': { borderColor: theme.palette.border.primary },
            }}
          >
            {t('Go to the Hub')}
          </Button>
          <Button
            variant="outlined"
            color="error"
            onClick={onDisconnect}
            disabled={!onDisconnect}
            sx={{
              textTransform: 'none',
              fontWeight: 600,
            }}
          >
            {t('Disconnect XTM Hub')}
          </Button>
        </div>
      </div>
    </div>
  );
};

export default XtmHubRegisteredSection;
