import { type FunctionComponent, useState } from 'react';

import { deleteNotifier, testNotifier, updateNotifier } from '../../../../actions/notifications/notifier-actions';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type NotifierInput, type NotifierOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import NotifierForm from './NotifierForm';

interface Props {
  notifier: NotifierOutput;
  onUpdate?: (result: NotifierOutput) => void;
  onDelete?: (result: string) => void;
}

const NotifierPopover: FunctionComponent<Props> = ({
  notifier,
  onUpdate,
  onDelete,
}) => {
  const { t } = useFormatter();
  const ability = useAbility();

  const [openEdit, setOpenEdit] = useState(false);
  const [openDelete, setOpenDelete] = useState(false);

  const submitEdit = (input: NotifierInput) => {
    updateNotifier(notifier.notifier_id, input).then((result: { data: NotifierOutput }) => {
      if (result) {
        onUpdate?.(result.data);
        setOpenEdit(false);
      }
      return result;
    });
  };

  const submitTest = () => {
    testNotifier(notifier.notifier_id).then(() => {
      MESSAGING$.notifySuccess(t('Test notification dispatched'));
    });
  };

  const submitDelete = () => {
    deleteNotifier(notifier.notifier_id).then(() => {
      onDelete?.(notifier.notifier_id);
      setOpenDelete(false);
    });
  };

  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);

  const entries: PopoverEntry[] = [
    {
      label: 'Test',
      action: submitTest,
      userRight: canManage,
    },
  ];
  if (!notifier.notifier_built_in) {
    entries.push(
      {
        label: 'Update',
        action: () => setOpenEdit(true),
        userRight: canManage,
      },
      {
        label: 'Delete',
        action: () => setOpenDelete(true),
        userRight: canManage,
      },
    );
  }

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <Drawer
        open={openEdit}
        handleClose={() => setOpenEdit(false)}
        title={t('Update the notifier')}
      >
        <NotifierForm
          editing
          initialValues={notifier}
          onSubmit={submitEdit}
        />
      </Drawer>
      <DialogDelete
        open={openDelete}
        handleClose={() => setOpenDelete(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this notifier?')}
      />
    </>
  );
};

export default NotifierPopover;
