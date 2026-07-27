import { NotificationAddOutlined, NotificationsActiveOutlined } from '@mui/icons-material';
import { IconButton, Tooltip } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { createNotificationTrigger, deleteNotificationTrigger, searchNotificationTriggers, updateNotificationTrigger } from '../../../../actions/notifications/notification-trigger-actions';
import { fetchNotifiers } from '../../../../actions/notifications/notifier-actions';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../../components/i18n';
import { type Filter, type NotificationTriggerInput, type NotificationTriggerOutput, type NotifierOutput } from '../../../../utils/api-types';
import useAuth from '../../../../utils/hooks/useAuth';
import TriggerForm from './TriggerForm';
import { SCENARIO_ONLY_EVENT_TYPES, TRIGGER_EVENT_TYPES } from './triggerUtils';

interface Props {
  resourceType: NotificationTriggerInput['notification_trigger_resource_type'];
  resourceId: string;
  resourceName: string;
}

/**
 * Instance-trigger subscription bell for entity headers: one click creates a
 * live trigger scoped to this exact entity (all event types, in-app notifier).
 * Once subscribed, clicking the bell opens an edit drawer (with a delete
 * button) so the subscription can be re-adjusted or removed - the OpenAEV
 * equivalent of OpenCTI's per-entity quick subscription.
 */
const TriggerSubscribeButton: FunctionComponent<Props> = ({
  resourceType,
  resourceId,
  resourceName,
}) => {
  const { t } = useFormatter();
  const { me } = useAuth();
  const [instanceTrigger, setInstanceTrigger] = useState<NotificationTriggerOutput | null>(null);
  const [openEdit, setOpenEdit] = useState(false);
  const [openDelete, setOpenDelete] = useState(false);

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
        // Scenarios also emit the semantic score-degradation event
        notification_trigger_event_types: [
          ...TRIGGER_EVENT_TYPES,
          ...(resourceType === 'SCENARIO' ? SCENARIO_ONLY_EVENT_TYPES : []),
        ],
        notification_trigger_instance_id: resourceId,
        notification_trigger_notifiers: uiNotifier ? [uiNotifier.notifier_id] : [],
      }).then((created: { data: NotificationTriggerOutput }) => {
        if (created) {
          setInstanceTrigger(created.data);
        }
      });
    });
  };

  const submitEdit = (input: NotificationTriggerInput) => {
    if (instanceTrigger) {
      updateNotificationTrigger(instanceTrigger.notification_trigger_id, input).then(
        (result: { data: NotificationTriggerOutput }) => {
          if (result) {
            setInstanceTrigger(result.data);
            setOpenEdit(false);
          }
          return result;
        },
      );
    }
  };

  const submitDelete = () => {
    if (instanceTrigger) {
      deleteNotificationTrigger(instanceTrigger.notification_trigger_id).then(() => {
        setInstanceTrigger(null);
        setOpenDelete(false);
        setOpenEdit(false);
      });
    }
  };

  return (
    <>
      <Tooltip title={instanceTrigger ? t('Manage notifications on this entity') : t('Subscribe to notifications on this entity')}>
        <IconButton
          size="small"
          color={instanceTrigger ? 'success' : 'primary'}
          onClick={instanceTrigger ? () => setOpenEdit(true) : subscribe}
        >
          {instanceTrigger
            ? <NotificationsActiveOutlined fontSize="small" />
            : <NotificationAddOutlined fontSize="small" />}
        </IconButton>
      </Tooltip>
      {instanceTrigger && (
        <Drawer
          open={openEdit}
          handleClose={() => setOpenEdit(false)}
          title={t('Update the notification trigger')}
        >
          <TriggerForm
            triggerType={instanceTrigger.notification_trigger_type ?? 'LIVE'}
            editing
            initialValues={instanceTrigger}
            onSubmit={submitEdit}
            onDelete={() => setOpenDelete(true)}
          />
        </Drawer>
      )}
      <DialogDelete
        open={openDelete}
        handleClose={() => setOpenDelete(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to unsubscribe from notifications on this entity?')}
      />
    </>
  );
};

export default TriggerSubscribeButton;
