import { CheckCircleOutlined, DeleteOutlined, NotificationsOutlined, UnpublishedOutlined } from '@mui/icons-material';
import {
  Badge,
  Button,
  Checkbox,
  Chip,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Tooltip,
} from '@mui/material';
import { type CSSProperties, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import {
  bulkDeleteNotifications,
  bulkMarkNotificationsRead,
  deleteNotification,
  markAllNotificationsRead,
  markNotificationRead,
  searchMyNotifications,
} from '../../../../actions/notifications/notification-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import DialogDelete from '../../../../components/common/DialogDelete';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type NotificationBulkProcessingInput, type NotificationOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import useEntityToggle from '../../../../utils/hooks/useEntityToggle';
import ToolBar from '../../common/ToolBar';
import DigestNotificationDialog from './DigestNotificationDialog';
import NotificationOperationChip from './NotificationOperationChip';
import { eventsOf, eventUrl, getFirstOperation, operationIcon } from './notificationUtils';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  notification_operation: { width: '12%' },
  notification_message: { width: '45%' },
  notification_created_at: { width: '20%' },
  notification_name: { width: '23%' },
};

const Notifications = () => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, fldt } = useFormatter();
  const [notifications, setNotifications] = useState<NotificationOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [notificationToDelete, setNotificationToDelete] = useState<NotificationOutput | null>(null);
  const [digestToOpen, setDigestToOpen] = useState<NotificationOutput | null>(null);

  const availableFilterNames = ['notification_name', 'notification_type', 'notification_is_read'];
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'my-notifications',
    buildSearchPagination({ sorts: initSorting('notification_created_at', 'DESC') }),
  );

  const searchNotificationsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchMyNotifications(input).finally(() => setLoading(false));
  };

  // Bulk selection (own notifications: always manageable)
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<NotificationOutput>('notification', notifications, queryableHelpers.paginationHelpers.getTotalElements());

  const buildBulkInput = (): NotificationBulkProcessingInput => ({
    search_pagination_input: selectAll ? searchPaginationInput : undefined,
    notification_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
    notification_ids_to_ignore: Object.keys(deSelectedElements),
  });

  const bulkDelete = () => {
    bulkDeleteNotifications(buildBulkInput()).then((result: { data: string[] }) => {
      const deletedIds = result.data ?? [];
      const newTotal = Math.max(0, queryableHelpers.paginationHelpers.getTotalElements() - deletedIds.length);
      setNotifications(notifications.filter(n => !deletedIds.includes(n.notification_id)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newTotal);
      handleClearSelectedElements();
    });
  };

  const bulkMarkRead = (read: boolean) => {
    bulkMarkNotificationsRead(buildBulkInput(), read).then((result: { data: string[] }) => {
      const updatedIds = result.data ?? [];
      setNotifications(notifications.map(n => (
        updatedIds.includes(n.notification_id)
          ? {
              ...n,
              notification_is_read: read,
            }
          : n
      )));
      handleClearSelectedElements();
    });
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
      queryableHelpers.paginationHelpers.handleChangeTotalElements(
        Math.max(0, queryableHelpers.paginationHelpers.getTotalElements() - 1),
      );
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

  // Digests open the details dialog; single-event notifications are real
  // router links to the entity that triggered them (ctrl/cmd+click friendly),
  // deleted entities are not navigable.
  const rowUrl = (notification: NotificationOutput): string | null => {
    if (notification.notification_type === 'DIGEST') {
      return null;
    }
    return eventUrl(eventsOf(notification).at(0)) ?? null;
  };

  const isRowClickable = (notification: NotificationOutput) => (
    notification.notification_type === 'DIGEST' || rowUrl(notification) !== null
  );

  // Headers
  const headers = [
    {
      field: 'notification_operation',
      label: 'Operation',
      isSortable: false,
      value: (notification: NotificationOutput) => (
        <NotificationOperationChip operation={getFirstOperation(notification)} />
      ),
    },
    {
      field: 'notification_message',
      label: 'Message',
      isSortable: false,
      value: (notification: NotificationOutput) => {
        const events = eventsOf(notification);
        if (notification.notification_type === 'DIGEST') {
          return <em>{t('Digest with {count} events', { count: String(events.length) })}</em>;
        }
        return events.at(0)?.message ?? '-';
      },
    },
    {
      field: 'notification_created_at',
      label: 'Original creation date',
      isSortable: true,
      value: (notification: NotificationOutput) => fldt(notification.notification_created_at),
    },
    {
      field: 'notification_name',
      label: 'Trigger name',
      isSortable: true,
      value: (notification: NotificationOutput) => (
        <Tooltip title={notification.notification_name ?? '-'}>
          <Chip
            style={{
              fontSize: 12,
              height: 20,
              width: 140,
              borderRadius: 4,
            }}
            color={notification.notification_type === 'LIVE' ? 'warning' : 'secondary'}
            variant="outlined"
            label={notification.notification_name ?? '-'}
            onClick={(event) => {
              // Quick filter on the trigger: only this trigger's notifications.
              event.preventDefault();
              event.stopPropagation();
              queryableHelpers.filterHelpers.handleAddSingleValueFilter('notification_name', notification.notification_name ?? '');
            }}
          />
        </Tooltip>
      ),
    },
  ];

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Profile') }, {
          label: t('Notifications'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={searchNotificationsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setNotifications}
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        entityPrefix="notification"
        topBarButtons={(
          <Button variant="outlined" color="primary" startIcon={<CheckCircleOutlined />} onClick={onMarkAllRead}>
            {t('Mark all as read')}
          </Button>
        )}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          sx={numberOfSelectedElements > 0
            ? {
                // Massive-operations toolbar: symmetric vertical padding keeps the
                // checkbox and actions vertically centered in the accent band.
                backgroundColor: 'background.accent',
                paddingBlock: 0.5,
              }
            : { paddingTop: 0 }}
          {...(numberOfSelectedElements === 0 ? { secondaryAction: <>&nbsp;</> } : {})}
        >
          <ListItemIcon style={{ minWidth: 40 }}>
            <Checkbox
              edge="start"
              checked={selectAll}
              disableRipple
              onChange={handleToggleSelectAll}
            />
          </ListItemIcon>
          {numberOfSelectedElements > 0 ? (
            <ListItemText
              primary={(
                <ToolBar
                  numberOfSelectedElements={numberOfSelectedElements}
                  handleClearSelectedElements={handleClearSelectedElements}
                  handleBulkDelete={bulkDelete}
                  canManage
                  deleteConfirmationSingular={t('Do you want to delete this notification?')}
                  deleteConfirmationPlural={t('Do you want to delete these {count} notifications?', { count: String(numberOfSelectedElements) })}
                  toolTasks={[
                    {
                      type: 'mark-read',
                      title: t('Mark as read'),
                      icon: () => <CheckCircleOutlined fontSize="small" />,
                      onClick: () => bulkMarkRead(true),
                    },
                    {
                      type: 'mark-unread',
                      title: t('Mark as unread'),
                      icon: () => <UnpublishedOutlined fontSize="small" />,
                      onClick: () => bulkMarkRead(false),
                    },
                  ]}
                />
              )}
            />
          ) : (
            <>
              <ListItemIcon />
              <ListItemText
                primary={(
                  <SortHeadersComponentV2
                    headers={headers}
                    inlineStylesHeaders={inlineStyles}
                    sortHelpers={queryableHelpers.sortHelpers}
                  />
                )}
              />
            </>
          )}
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={NotificationsOutlined} headers={headers} headerStyles={inlineStyles} withCheckbox />
          : notifications.map((notification) => {
              const clickable = isRowClickable(notification);
              const url = rowUrl(notification);
              return (
                <ListItem
                  key={notification.notification_id}
                  divider
                  disablePadding
                  secondaryAction={(
                    <>
                      <Tooltip title={notification.notification_is_read ? t('Mark as unread') : t('Mark as read')}>
                        <IconButton
                          onClick={() => onToggleRead(notification)}
                          size="small"
                          color={notification.notification_is_read ? 'primary' : 'success'}
                        >
                          {notification.notification_is_read
                            ? <UnpublishedOutlined fontSize="small" />
                            : <CheckCircleOutlined fontSize="small" />}
                        </IconButton>
                      </Tooltip>
                      <Tooltip title={t('Delete')}>
                        <IconButton
                          onClick={() => setNotificationToDelete(notification)}
                          size="small"
                          color="error"
                        >
                          <DeleteOutlined fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </>
                  )}
                >
                  <ListItemButton
                    classes={{ root: classes.item }}
                    {...(url
                      ? {
                          component: Link,
                          to: url,
                        }
                      : { onClick: () => notification.notification_type === 'DIGEST' && setDigestToOpen(notification) })}
                    sx={clickable ? undefined : { cursor: 'default' }}
                  >
                    <ListItemIcon
                      style={{ minWidth: 40 }}
                      onClick={event => onToggleEntity(notification, event)}
                    >
                      <Checkbox
                        edge="start"
                        checked={
                          (selectAll && !(notification.notification_id in (deSelectedElements || {})))
                          || notification.notification_id in (selectedElements || {})
                        }
                        disableRipple
                      />
                    </ListItemIcon>
                    <ListItemIcon>
                      {/* Unread = small dot on the notification icon (OpenCTI-style) */}
                      <Badge color="warning" variant="dot" invisible={notification.notification_is_read}>
                        {operationIcon(getFirstOperation(notification))}
                      </Badge>
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div style={bodyItemsStyles.bodyItems}>
                          {headers.map(header => (
                            <div
                              key={header.field}
                              style={{
                                ...bodyItemsStyles.bodyItem,
                                ...inlineStyles[header.field],
                              }}
                            >
                              {header.value(notification)}
                            </div>
                          ))}
                        </div>
                      )}
                    />
                  </ListItemButton>
                </ListItem>
              );
            })}
      </List>
      <DialogDelete
        open={notificationToDelete !== null}
        handleClose={() => setNotificationToDelete(null)}
        handleSubmit={() => {
          if (notificationToDelete) {
            onDelete(notificationToDelete);
          }
          setNotificationToDelete(null);
        }}
        text={t('Do you want to delete this notification?')}
      />
      <DigestNotificationDialog
        notification={digestToOpen}
        onClose={() => setDigestToOpen(null)}
      />
    </>
  );
};

export default Notifications;
