import { Box, Link, List, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import VideoPlayer from './VideoPlayer';

const GettingStartedSummary = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const videoLink = 'https://www.youtube.com/embed/wb_v7sa7y8w?rel=0&modestbranding=1&loop=1&playlist=wb_v7sa7y8w';

  return (
    <Box sx={{ overflow: 'hidden' }}>
      <Typography variant="h1">
        {t('getting_started_welcome')}
      </Typography>
      <Paper
        variant="outlined"
        sx={{ p: 2 }}
      >
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: theme.spacing(1),
        }}
        >
          <Box style={{
            display: 'flex',
            flexDirection: 'column',
            gap: theme.spacing(2),
          }}
          >
            {t('getting_started_description_text')}
            <List sx={{
              listStyleType: 'disc',
              pl: 3,
            }}
            >
              <li>{t('getting_started_description_first_task_text')}</li>
              <li>{t('getting_started_description_scenario_text')}</li>
              <li>{t('getting_started_description_end_text')}</li>
              <li>{t('getting_started_description_test_scenarios_text', { xtmHubLink: <Link href="https://hub.filigran.io/cybersecurity-solutions/open-bas-scenarios">{t('XTM Hub Library')}</Link> })}</li>
            </List>
            {t('getting_started_description_conclusion_text')}
          </Box>
          <VideoPlayer videoLink={videoLink} />
        </Box>
      </Paper>
    </Box>
  );
};

export default GettingStartedSummary;
