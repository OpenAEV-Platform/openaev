import { AlarmOnOutlined, NotificationsOutlined } from '@mui/icons-material';
import { Tab, Tabs } from '@mui/material';
import { Link } from 'react-router';

import { useFormatter } from '../../../components/i18n';

interface Props { current: 'alerts' | 'triggers' }

/**
 * Notification center tabs: alerts and triggers are two views of the same
 * center. Navigation goes through router links so existing deep links to
 * /admin/profile/notifications and /admin/profile/triggers keep working.
 */
const NotificationCenterTabs = ({ current }: Props) => {
  const { t } = useFormatter();

  return (
    <Tabs
      value={current}
      sx={{
        borderBottom: 1,
        borderColor: 'divider',
      }}
    >
      <Tab
        component={Link}
        to="/admin/profile/notifications"
        value="alerts"
        label={t('Alerts')}
        icon={<NotificationsOutlined fontSize="small" />}
        iconPosition="start"
        // The theme forces `display: inline-block` + lowercase on MuiTab for
        // its `::first-letter` trick; restore the flex row so the icon and
        // label align, and keep the already-capitalised label verbatim.
        sx={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          textTransform: 'none',
          minHeight: 48,
          fontSize: 13,
        }}
      />
      <Tab
        component={Link}
        to="/admin/profile/triggers"
        value="triggers"
        label={t('Triggers')}
        icon={<AlarmOnOutlined fontSize="small" />}
        iconPosition="start"
        sx={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          textTransform: 'none',
          minHeight: 48,
          fontSize: 13,
        }}
      />
    </Tabs>
  );
};

export default NotificationCenterTabs;
