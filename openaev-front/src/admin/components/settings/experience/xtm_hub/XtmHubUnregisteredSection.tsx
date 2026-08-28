import { MapOutlined, RocketLaunchOutlined, VideoLibraryOutlined, WidgetsOutlined } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { ExperienceHeadline } from '../ExperienceCard';
import ExperienceFeatureTile from '../ExperienceFeatureTile';

const XtmHubUnregisteredSection: React.FC = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const accent = theme.palette.xtmhub.main;

  return (
    <>
      <ExperienceHeadline>
        {t('Extend and scale your OpenAEV experience')}
      </ExperienceHeadline>
      <Typography variant="body2" color="text.secondary">
        {t('Connect OpenAEV to XTMHub to deploy pre-configured actions and scenarios in one click, start free trials, and get more out of your XTM platform.')}
      </Typography>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
          gap: theme.spacing(1.5),
        }}
      >
        <ExperienceFeatureTile accent={accent} icon={<RocketLaunchOutlined />} label={t('XTM Platform free trial')} />
        <ExperienceFeatureTile accent={accent} icon={<WidgetsOutlined />} label={t('Pre-built content')} />
        <ExperienceFeatureTile accent={accent} icon={<MapOutlined />} label={t('XTM Platform Roadmap')} />
        <ExperienceFeatureTile accent={accent} icon={<VideoLibraryOutlined />} label={t('Academy')} />
      </div>
    </>
  );
};

export default XtmHubUnregisteredSection;
