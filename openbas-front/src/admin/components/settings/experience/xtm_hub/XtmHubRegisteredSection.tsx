import { List, ListItem, ListItemText } from '@mui/material';
import type React from 'react';

import { useFormatter } from '../../../../../components/i18n';
import ItemBoolean from '../../../../../components/ItemBoolean';
import useAuth from '../../../../../utils/hooks/useAuth';

const XtmHubRegisteredSection: React.FC = () => {
  const { t, fd } = useFormatter();
  const { settings } = useAuth();

  return (
    <List style={{ padding: 0 }}>
      {settings.xtm_hub_registration_status === 'registered' && (
        <>
          <ListItem divider={true}>
            <ListItemText primary={t('Registration status')} />
            <ItemBoolean
              variant="xlarge"
              label={t('Registered')}
              status={true}
            />
          </ListItem>
          <ListItem divider={true}>
            <ListItemText primary={t('Registration date')} />
            <ItemBoolean
              variant="xlarge"
              neutralLabel={fd(settings.xtm_hub_registration_date)}
              status={null}
            />
          </ListItem>
          <ListItem divider={true}>
            <ListItemText primary={t('Registered by')} />
            <ItemBoolean
              variant="xlarge"
              neutralLabel={settings.xtm_hub_registration_user_name}
              status={null}
            />
          </ListItem>
        </>
      )}
      {settings.xtm_hub_registration_status === 'lost_connectivity' && (
        <>
          <ListItem divider={true}>
            <ListItemText primary={t('Registration status')} />
            <ItemBoolean
              variant="xlarge"
              label={t('Connectivity lost')}
              status={false}
            />
          </ListItem>
        </>
      )}
    </List>
  );
};

export default XtmHubRegisteredSection;
