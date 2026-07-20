import { MapOutlined, RocketLaunchOutlined, VideoLibraryOutlined, WidgetsOutlined } from '@mui/icons-material';
import { Button, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { XTM_HUB_DEFAULT_URL } from '../../../../../utils/Environment';
import useAuth from '../../../../../utils/hooks/useAuth';
import XtmHubFeatureCard from './XtmHubFeatureCard';
import { getXtmHubLogo } from './XtmHubUtils';

interface XtmHubUnregisteredSectionProps { onConnect?: () => void }

const XtmHubUnregisteredSection: React.FC<XtmHubUnregisteredSectionProps> = ({ onConnect }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { settings } = useAuth();
  const xtmHubLogo = getXtmHubLogo(theme);
  const hubUrl = settings?.xtm_hub_url ?? XTM_HUB_DEFAULT_URL;

  const featureIconSx = {
    fontSize: 20,
    color: theme.palette.text.primary,
  };

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
            {t('Extend and scale your OpenAEV experience')}
          </Typography>
          <Typography variant="body1">
            {t('Connect OpenAEV to XTMHub to deploy pre-configured actions and scenarios in one click, start free trials, and get more out of your XTM platform.')}
          </Typography>

          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(4, 1fr)',
            gap: theme.spacing(2),
          }}
          >
            <XtmHubFeatureCard icon={<RocketLaunchOutlined sx={featureIconSx} />} label={t('XTM Platform free trial')} />
            <XtmHubFeatureCard icon={<WidgetsOutlined sx={featureIconSx} />} label={t('Pre-built content')} />
            <XtmHubFeatureCard icon={<MapOutlined sx={featureIconSx} />} label={t('XTM Platform Roadmap')} />
            <XtmHubFeatureCard icon={<VideoLibraryOutlined sx={featureIconSx} />} label={t('Academy')} />
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
            {t('Explore XTM Hub')}
          </Button>
          <Button
            variant="contained"
            onClick={onConnect}
            disabled={!onConnect}
            sx={{
              textTransform: 'none',
              fontWeight: 600,
            }}
          >
            {t('Connect to XTM Hub')}
          </Button>
        </div>
      </div>
    </div>
  );
};

export default XtmHubUnregisteredSection;
