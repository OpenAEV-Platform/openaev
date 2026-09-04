import { NotificationsOutlined } from '@mui/icons-material';
import { Badge, IconButton, Tooltip } from '@mui/material';
import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router';

import { getUnreadNotificationsCount } from '../../../actions/notifications/notification-actions';
import { type NotificationHelper } from '../../../actions/notifications/notification-helper';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';

interface Props { iconButtonSx: (selected: boolean) => object }

/**
 * Top bar bell: unread notifications badge, refreshed live through the SSE
 * stream (new notifications land in the redux `notifications` map via the
 * shared data loader, which retriggers the count fetch).
 */
const TopBarNotifications = ({ iconButtonSx }: Props) => {
  const { t } = useFormatter();
  const location = useLocation();
  const [unreadCount, setUnreadCount] = useState(0);

  const notifications = useHelper((helper: NotificationHelper) => helper.getNotifications());

  useEffect(() => {
    getUnreadNotificationsCount().then((result: { data: number }) => setUnreadCount(result.data ?? 0));
  }, [notifications]);

  return (
    <Tooltip title={t('Notifications')}>
      <IconButton
        aria-label="notifications"
        component={Link}
        to="/admin/profile/notifications"
        sx={iconButtonSx(
          // The bell is the single entry point of the notification center:
          // both its tabs (alerts and triggers) light it up.
          location.pathname.startsWith('/admin/profile/notifications')
          || location.pathname.startsWith('/admin/profile/triggers'),
        )}
      >
        <Badge color="secondary" variant="dot" invisible={unreadCount === 0}>
          <NotificationsOutlined fontSize="medium" />
        </Badge>
      </IconButton>
    </Tooltip>
  );
};

export default TopBarNotifications;
