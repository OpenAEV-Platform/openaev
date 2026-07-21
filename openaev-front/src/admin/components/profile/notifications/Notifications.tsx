import { CheckCircleOutlined, DeleteOutlined, DraftsOutlined, InboxOutlined, MailOutlined, NotificationsOutlined } from '@mui/icons-material';
import { Button, Chip, Collapse, IconButton, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip, Typography } from '@mui/material';
import { useState } from 'react';

import { deleteNotification, markAllNotificationsRead, markNotificationRead, searchMyNotifications } from '../../../../actions/notifications/notification-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import { type NotificationOutput } from '../../../../utils/api-types';
import NotificationTabs from './NotificationTabs';

interface ContentEvent {
  operation?: string;
  message?: string;
  resource_type?: string;
  resource_id?: string;
}

interface ContentGroup {
  title?: string;
  events?: ContentEvent[];
}

const Notifications = () => {
  const { t, nsdt } = useFormatter();
  const [notifications, setNotifications] = useState<NotificationOutput[]>([]);
  const [expandedIds, setExpandedIds] = useState<string[]>([]);

  const availableFilterNames = ['notification_name', 'notification_type', 'notification_is_read'];
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'my-notifications',
    buildSearchPagination({
      sorts: [{
        property: 'notification_created_at',
        direction: 'DESC',
      }],
    }),
  );

  const toggleExpand = (id: string) => {
    setExpandedIds(expandedIds.includes(id) ? expandedIds.filter(existing => existing !== id) : [...expandedIds, id]);
  };

  const onToggleRead = (notification: NotificationOutput) => {
    const read = !notification.notification_is_read;
    markNotificationRead(notification.notification_id, read).then(() => {
      setNotifications(notifications.map(existing => (
        existing.notification_id === notification.notification_id
          ? {
              ...existing,
              notification_is_read: read,
            }
          : existing
      )));
    });
  };

  const onDelete = (notification: NotificationOutput) => {
    deleteNotification(notification.notification_id).then(() => {
      setNotifications(notifications.filter(existing => existing.notification_id !== notification.notification_id));
    });
  };

  const onMarkAllRead = () => {
    markAllNotificationsRead().then(() => {
      setNotifications(notifications.map(existing => ({
        ...existing,
        notification_is_read: true,
      })));
    });
  };

  const eventsOf = (notification: NotificationOutput): ContentGroup[] => (notification.notification_content ?? []) as ContentGroup[];
  const totalEvents = (notification: NotificationOutput) =>
    eventsOf(notification).reduce((acc, group) => acc + (group.events?.length ?? 0), 0);

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Profile') }, {
          label: t('Notifications'),
          current: true,
        }]}
      />
      <NotificationTabs />
      <PaginationComponentV2
        fetch={searchMyNotifications}
        searchPaginationInput={searchPaginationInput}
        setContent={setNotifications}
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        entityPrefix="notification"
        topBarButtons={(
          <Button
            variant="outlined"
            color="primary"
            startIcon={<CheckCircleOutlined />}
            onClick={onMarkAllRead}
          >
            {t('Mark all as read')}
          </Button>
        )}
      />
      <List>
        {notifications.map((notification) => {
          const expanded = expandedIds.includes(notification.notification_id);
          const groups = eventsOf(notification);
          const isDigest = notification.notification_type === 'DIGEST';
          return (
            <div key={notification.notification_id}>
              <ListItem
                divider
                disablePadding
                secondaryAction={(
                  <>
                    <Tooltip title={notification.notification_is_read ? t('Mark as unread') : t('Mark as read')}>
                      <IconButton onClick={() => onToggleRead(notification)} size="small">
                        {notification.notification_is_read ? <MailOutlined fontSize="small" /> : <DraftsOutlined fontSize="small" />}
                      </IconButton>
                    </Tooltip>
                    <Tooltip title={t('Delete')}>
                      <IconButton onClick={() => onDelete(notification)} size="small" color="error">
                        <DeleteOutlined fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </>
                )}
              >
                <ListItemButton onClick={() => toggleExpand(notification.notification_id)}>
                  <ListItemIcon>
                    {isDigest
                      ? <InboxOutlined color={notification.notification_is_read ? 'disabled' : 'primary'} />
                      : <NotificationsOutlined color={notification.notification_is_read ? 'disabled' : 'primary'} />}
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <span style={{ fontWeight: notification.notification_is_read ? 400 : 600 }}>
                        {notification.notification_name}
                      </span>
                    )}
                    secondary={nsdt(notification.notification_created_at)}
                  />
                  <Chip
                    label={isDigest ? `${t('Digest')} (${totalEvents(notification)})` : t('Live')}
                    size="small"
                    variant="outlined"
                    color={isDigest ? 'secondary' : 'primary'}
                    style={{ marginRight: 16 }}
                  />
                </ListItemButton>
              </ListItem>
              <Collapse in={expanded} timeout="auto" unmountOnExit>
                <div style={{ padding: '8px 16px 16px 72px' }}>
                  {groups.map(group => (
                    <div key={`${notification.notification_id}-${group.title}`}>
                      {isDigest && (
                        <Typography variant="subtitle2" style={{ marginTop: 8 }}>
                          {group.title}
                        </Typography>
                      )}
                      <List dense disablePadding>
                        {(group.events ?? []).map(event => (
                          <ListItem key={`${event.resource_id}-${event.message}`} disableGutters>
                            <ListItemText primary={event.message} />
                          </ListItem>
                        ))}
                      </List>
                    </div>
                  ))}
                </div>
              </Collapse>
            </div>
          );
        })}
      </List>
    </>
  );
};

export default Notifications;
