import { List, ListItem, ListItemText } from '@mui/material';
import type React from 'react';

import type { LoggedHelper } from '../../../../../actions/helper';
import { useFormatter } from '../../../../../components/i18n';
import ItemBoolean from '../../../../../components/ItemBoolean';
import { useHelper } from '../../../../../store';

const XtmHubRegisteredSection: React.FC = () => {
  const { t, fd } = useFormatter();
  const registration = useHelper((helper: LoggedHelper) => helper.getXtmHubRegistration());

  return (
    <List style={{ padding: 0 }}>
      {registration?.tenant_xtmhub_registration_status === 'REGISTERED' && (
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
              neutralLabel={fd(registration.tenant_xtmhub_registration_date)}
              status={null}
            />
          </ListItem>
          <ListItem divider={true}>
            <ListItemText primary={t('Registered by')} />
            <ItemBoolean
              variant="xlarge"
              neutralLabel={registration.tenant_xtmhub_registration_user_name}
              status={null}
            />
          </ListItem>
        </>
      )}
      {registration?.tenant_xtmhub_registration_status === 'LOST_CONNECTIVITY' && (
        <>
          <ListItem divider={true}>
            <ListItemText primary={t('Registration status')} />
            <ItemBoolean
              variant="xlarge"
              label={t('Connectivity lost')}
              status={false}
            />
          </ListItem>
          <ListItem divider={true}>
            <ListItemText primary={t('Last successful check')} />
            <ItemBoolean
              variant="xlarge"
              neutralLabel={fd(registration.tenant_xtmhub_registration_last_connectivity_check)}
              status={null}
            />
          </ListItem>
        </>
      )}
    </List>
  );
};

export default XtmHubRegisteredSection;
