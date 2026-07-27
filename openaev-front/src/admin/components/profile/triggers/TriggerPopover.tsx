import { type FunctionComponent, useState } from 'react';

import { deleteNotificationTrigger, updateNotificationTrigger } from '../../../../actions/notifications/notification-trigger-actions';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type NotificationTriggerInput, type NotificationTriggerOutput } from '../../../../utils/api-types';
import TriggerForm from './TriggerForm';

interface Props {
  trigger: NotificationTriggerOutput;
  onUpdate?: (result: NotificationTriggerOutput) => void;
  onDelete?: (result: string) => void;
}

const TriggerPopover: FunctionComponent<Props> = ({
  trigger,
  onUpdate,
  onDelete,
}) => {
  const { t } = useFormatter();

  const [openEdit, setOpenEdit] = useState(false);
  const [openDelete, setOpenDelete] = useState(false);

  const submitEdit = (input: NotificationTriggerInput) => {
    updateNotificationTrigger(trigger.notification_trigger_id, input).then(
      (result: { data: NotificationTriggerOutput }) => {
        if (result) {
          onUpdate?.(result.data);
          setOpenEdit(false);
        }
        return result;
      },
    );
  };

  const submitToggleEnabled = () => {
    const input: NotificationTriggerInput = {
      notification_trigger_name: trigger.notification_trigger_name ?? '',
      notification_trigger_type: trigger.notification_trigger_type ?? 'LIVE',
      notification_trigger_enabled: !trigger.notification_trigger_enabled,
      notification_trigger_resource_type: trigger.notification_trigger_resource_type,
      notification_trigger_event_types: trigger.notification_trigger_event_types,
      notification_trigger_filters: trigger.notification_trigger_filters,
      notification_trigger_instance_id: trigger.notification_trigger_instance_id,
      notification_trigger_period: trigger.notification_trigger_period,
      notification_trigger_time: trigger.notification_trigger_time,
      notification_trigger_children: trigger.notification_trigger_children,
      notification_trigger_notifiers: trigger.notification_trigger_notifiers,
    };
    updateNotificationTrigger(trigger.notification_trigger_id, input).then(
      (result: { data: NotificationTriggerOutput }) => {
        if (result) {
          onUpdate?.(result.data);
        }
        return result;
      },
    );
  };

  const submitDelete = () => {
    deleteNotificationTrigger(trigger.notification_trigger_id).then(() => {
      onDelete?.(trigger.notification_trigger_id);
      setOpenDelete(false);
    });
  };

  const entries: PopoverEntry[] = [
    {
      label: 'Update',
      action: () => setOpenEdit(true),
      userRight: true,
    },
    {
      label: trigger.notification_trigger_enabled ? 'Disable' : 'Enable',
      action: submitToggleEnabled,
      userRight: true,
    },
    {
      label: 'Delete',
      action: () => setOpenDelete(true),
      userRight: true,
    },
  ];

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <Drawer
        open={openEdit}
        handleClose={() => setOpenEdit(false)}
        title={t('Update the notification trigger')}
      >
        <TriggerForm
          triggerType={trigger.notification_trigger_type ?? 'LIVE'}
          editing
          initialValues={trigger}
          onSubmit={submitEdit}
        />
      </Drawer>
      <DialogDelete
        open={openDelete}
        handleClose={() => setOpenDelete(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this notification trigger?')}
      />
    </>
  );
};

export default TriggerPopover;
