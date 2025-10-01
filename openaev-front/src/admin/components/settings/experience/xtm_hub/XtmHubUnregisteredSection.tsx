import { List, ListItem, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';

import { useFormatter } from '../../../../../components/i18n';
import GradientButton from '../../../common/GradientButton';

const XtmHubUnregisteredSection: React.FC = () => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <>
      <Typography>{t('By registering this platform into the hub, it will allow you to:')}</Typography>
      <List
        style={{
          listStyleType: 'disc',
          marginLeft: theme.spacing(2),
        }}
        dense
      >
        <ListItem style={{
          display: 'list-item',
          paddingLeft: 0,
          marginLeft: theme.spacing(2),
        }}
        >
          {t('Deploy in one-click threat management resources such as scenarios')}
        </ListItem>
        <ListItem style={{
          display: 'list-item',
          paddingLeft: 0,
          marginLeft: theme.spacing(2),
        }}
        >
          <span>
            {t('Stay informed of new resources and key threat events with an exclusive news feed')}
            <i>
              {t(' (coming soon)')}
            </i>
          </span>
        </ListItem>
        <ListItem style={{
          display: 'list-item',
          paddingLeft: 0,
          marginLeft: theme.spacing(2),
        }}
        >
          <span>
            {t('Monitor key metrics of the platform and health status')}
            <i>
              {t(' (coming soon)')}
            </i>
          </span>
        </ListItem>
      </List>

      <GradientButton
        variant="outlined"
        component="a"
        href="https://filigran.io/platforms/xtm-hub/"
        target="_blank"
        rel="noreferrer"
        aria-label={t('Discover the Hub (external link)')}
        style={{
          marginTop: theme.spacing(1),
          marginBottom: theme.spacing(1),
        }}
      >
        {t('Discover the Hub')}
      </GradientButton>
    </>
  );
};

export default XtmHubUnregisteredSection;
