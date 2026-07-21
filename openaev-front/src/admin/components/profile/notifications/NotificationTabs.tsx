import { Tab, Tabs } from '@mui/material';
import { Link, useLocation } from 'react-router';

import { useFormatter } from '../../../../components/i18n';

/** Shared tab bar switching between the notifications feed and the triggers management. */
const NotificationTabs = () => {
  const { t } = useFormatter();
  const location = useLocation();
  const current = location.pathname.startsWith('/admin/profile/triggers')
    ? '/admin/profile/triggers'
    : '/admin/profile/notifications';
  return (
    <Tabs value={current} style={{ marginBottom: 16 }}>
      <Tab
        label={t('Notifications')}
        value="/admin/profile/notifications"
        component={Link}
        to="/admin/profile/notifications"
      />
      <Tab
        label={t('Triggers')}
        value="/admin/profile/triggers"
        component={Link}
        to="/admin/profile/triggers"
      />
    </Tabs>
  );
};

export default NotificationTabs;
