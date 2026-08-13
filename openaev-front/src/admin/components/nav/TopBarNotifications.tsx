import { Badge, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { NotificationsOutlined } from '@mui/icons-material';
import { useEffect, useState } from 'react';
import { useLocation } from 'react-router';

import { getUnreadNotificationsCount } from '../../../actions/notifications/notification-actions';
import { type NotificationHelper } from '../../../actions/notifications/notification-helper';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import TopBarIconLink from './TopBarIconLink';

/**
 * Top bar bell: unread notifications badge, refreshed live through the SSE
 * stream (new notifications land in the redux `notifications` map via the
 * shared data loader, which retriggers the count fetch).
 */
const TopBarNotifications = () => {
  const { t } = useFormatter();
  const location = useLocation();
  const [unreadCount, setUnreadCount] = useState(0);

  const notifications = useHelper((helper: NotificationHelper) => helper.getNotifications());

  useEffect(() => {
    getUnreadNotificationsCount().then((result: { data: number }) => setUnreadCount(result.data ?? 0));
  }, [notifications]);

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        {/* The Badge wraps the LINK, not the glyph: it describes its child, and the glyph slot is aria-hidden. */}
        <Badge content={unreadCount} dot invisible={unreadCount === 0}>
          <TopBarIconLink
            aria-label="notifications"
            to="/admin/profile/notifications"
            active={location.pathname.startsWith('/admin/profile/notifications')}
            icon={<NotificationsOutlined fontSize="medium" />}
          />
        </Badge>
      </TooltipTrigger>
      <TooltipContent>{t('Notifications')}</TooltipContent>
    </Tooltip>
  );
};

export default TopBarNotifications;
