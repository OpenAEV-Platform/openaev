import { Box, List, Paper, Typography } from '@mui/material';

import { useFormatter } from '../../../components/i18n';
import VideoPlayer from './VideoPlayer';

const GettingStartedSummary = () => {
  const { t } = useFormatter();

  return (
    <Box>
      <Typography variant="h1">
        {t('getting_started_welcome')}
      </Typography>
      <Paper
        variant="outlined"
        sx={{ p: 2 }}
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
        }}
      >
        <div>
          {t('getting_started_description')}
          <List sx={{
            listStyleType: 'disc',
            pl: 3,
          }}
          >
            <li>{t('getting_started_oaev')}</li>
            <li>{t('getting_started_usage')}</li>
            <li>{t('getting_started_demonstration')}</li>
            <li>{t('getting_started_explanation')}</li>
          </List>
        </div>
        <VideoPlayer videoLink="https://app.storylane.io/demo/bxqijbtlfklz" />
      </Paper>
    </Box>
  );
};

export default GettingStartedSummary;
