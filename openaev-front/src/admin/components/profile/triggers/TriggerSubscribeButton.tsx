import { NotificationAddOutlined, NotificationsActiveOutlined } from '@mui/icons-material';
import { IconButton, Tooltip } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { createNotificationTrigger, deleteNotificationTrigger, searchNotificationTriggers } from '../../../../actions/notifications/notification-trigger-actions';
import { fetchNotifiers } from '../../../../actions/notifications/notifier-actions';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../../components/i18n';
import { type Filter, type NotificationTriggerInput, type NotificationTriggerOutput, type NotifierOutput } from '../../../../utils/api-types';
import useAuth from '../../../../utils/hooks/useAuth';

interface Props {
  resourceType: NotificationTriggerInput['notification_trigger_resource_type'];
  resourceId: string;
  resourceName: string;
}

/**
 * Instance-trigger subscription bell for entity headers: one click creates a
 * live trigger scoped to this exact entity (all event types, in-app notifier),
 * a second click removes it - the OpenAEV equivalent of OpenCTI's per-entity
 * subscription.
 */
const TriggerSubscribeButton: FunctionComponent<Props> = ({
  resourceType,
  resourceId,
  resourceName,
}) => {
  const { t } = useFormatter();
  const { me } = useAuth();
  const [instanceTrigger, setInstanceTrigger] = useState<NotificationTriggerOutput | null>(null);

  useEffect(() => {
    // Server-side lookup of my instance trigger on this exact entity: the search endpoint
    // returns every trigger of the tenant for admins, so the owner filter is required.
    const eqFilter = (key: string, value: string): Filter => ({
      id: generateFilterId(),
      key,
      mode: 'and',
      operator: 'eq',
      values: [value],
    });
    const filters: Filter[] = [
      eqFilter('notification_trigger_instance_id', resourceId),
      eqFilter('notification_trigger_owner', me.user_id),
    ];
    if (resourceType) {
      filters.push(eqFilter('notification_trigger_resource_type', resourceType));
    }
    searchNotificationTriggers(buildSearchPagination({
      size: 1,
      filterGroup: {
        mode: 'and',
        filters,
      },
    }))
      .then((result: { data: { content?: NotificationTriggerOutput[] } }) => {
        setInstanceTrigger(result.data.content?.[0] ?? null);
      });
  }, [resourceId, resourceType, me.user_id]);

  const subscribe = () => {
    fetchNotifiers().then((result: { data: NotifierOutput[] }) => {
      const uiNotifier = (result.data ?? []).find(notifier => notifier.notifier_type === 'UI' && notifier.notifier_built_in);
      createNotificationTrigger({
        notification_trigger_name: `${t('Instance trigger:')} ${resourceName}`,
        notification_trigger_type: 'LIVE',
        notification_trigger_resource_type: resourceType,
        notification_trigger_event_types: ['CREATE', 'UPDATE', 'DELETE'],
        notification_trigger_instance_id: resourceId,
        notification_trigger_notifiers: uiNotifier ? [uiNotifier.notifier_id] : [],
      }).then((created: { data: NotificationTriggerOutput }) => {
        if (created) {
          setInstanceTrigger(created.data);
        }
      });
    });
  };

  const unsubscribe = () => {
    if (instanceTrigger) {
      deleteNotificationTrigger(instanceTrigger.notification_trigger_id).then(() => {
        setInstanceTrigger(null);
      });
    }
  };

  return (
    <Tooltip title={instanceTrigger ? t('Unsubscribe from notifications on this entity') : t('Subscribe to notifications on this entity')}>
      <IconButton
        size="small"
        color={instanceTrigger ? 'success' : 'primary'}
        onClick={instanceTrigger ? unsubscribe : subscribe}
      >
        {instanceTrigger
          ? <NotificationsActiveOutlined fontSize="small" />
          : <NotificationAddOutlined fontSize="small" />}
      </IconButton>
    </Tooltip>
  );
};

export default TriggerSubscribeButton;
