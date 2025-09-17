import { List, Paper, Typography } from '@mui/material';

import { useFormatter } from '../../../components/i18n';

const GettingStartedScenarios = () => {
  const { t } = useFormatter();

  return (
    <>
      <Typography variant="h1">
        {t('getting_started_scenarios')}
      </Typography>
      <Typography variant="h4">
        {t('getting_started_scenarios_explanation')}
      </Typography>
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr 1fr',
      }}
      >
        <Paper variant="outlined" sx={{ p: 2 }}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
          }}
          >
            <div>
              <Typography>
                {t('getting_started_description')}
              </Typography>
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
          </div>
        </Paper>
      </div>
    </>
  );
};

export default GettingStartedScenarios;
